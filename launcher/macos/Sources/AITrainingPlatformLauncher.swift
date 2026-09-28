import AppKit
import Foundation
import Network

private let workspaceOverrideKey = "APP_WORKSPACE_ROOT"

private struct ServiceDefinition {
    let key: String
    let title: String
    let port: UInt16
    let healthURL: URL?
    let command: String
    let workingDirectory: URL
    let logFile: URL
}

private final class ProcessSupervisor {
    private(set) var processes: [String: Process] = [:]
    private(set) var ownedKeys = Set<String>()
    private let queue = DispatchQueue(label: "com.xgls.ai-training-launcher.processes")
    var hasManagedProcesses: Bool { queue.sync { processes.values.contains(where: \.isRunning) } }

    func start(_ service: ServiceDefinition, environment: [String: String], onOutput: @escaping (String) -> Void, onExit: @escaping (Int32) -> Void) throws -> Process? {
        if isPortOpen(service.port) {
            onOutput("检测到 \(service.title) 已在运行，复用端口 \(service.port)。")
            return nil
        }

        let logDirectory = service.logFile.deletingLastPathComponent()
        try FileManager.default.createDirectory(at: logDirectory, withIntermediateDirectories: true)
        FileManager.default.createFile(atPath: service.logFile.path, contents: nil)
        let logHandle = try FileHandle(forWritingTo: service.logFile)
        try logHandle.truncate(atOffset: 0)

        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/bin/zsh")
        process.arguments = ["-c", "exec \(service.command)"]
        process.currentDirectoryURL = service.workingDirectory
        process.environment = environment
        let pipe = Pipe()
        process.standardOutput = pipe
        process.standardError = pipe
        pipe.fileHandleForReading.readabilityHandler = { handle in
            let data = handle.availableData
            guard !data.isEmpty, let text = String(data: data, encoding: .utf8) else { return }
            logHandle.write(data)
            DispatchQueue.main.async { onOutput(text.trimmingCharacters(in: .whitespacesAndNewlines)) }
        }
        process.terminationHandler = { [weak self] process in
            pipe.fileHandleForReading.readabilityHandler = nil
            try? logHandle.close()
            self?.queue.async {
                self?.processes.removeValue(forKey: service.key)
                self?.ownedKeys.remove(service.key)
            }
            DispatchQueue.main.async { onExit(process.terminationStatus) }
        }
        try process.run()
        queue.sync {
            processes[service.key] = process
            ownedKeys.insert(service.key)
        }
        onOutput("已在后台启动 \(service.title)，PID \(process.processIdentifier)。")
        return process
    }

    func stopAll(onOutput: @escaping (String) -> Void) -> [String: Process] {
        let current = queue.sync { processes }
        for (key, process) in current {
            guard process.isRunning else { continue }
            onOutput("正在停止 \(key)（PID \(process.processIdentifier)）。")
            process.terminate()
        }
        return current
    }

    func isPortOpen(_ port: UInt16) -> Bool {
        let semaphore = DispatchSemaphore(value: 0)
        let resultLock = NSLock()
        var result = false
        let connection = NWConnection(host: "127.0.0.1", port: NWEndpoint.Port(rawValue: port)!, using: .tcp)
        connection.stateUpdateHandler = { state in
            switch state {
            case .ready:
                resultLock.lock()
                result = true
                resultLock.unlock()
                connection.cancel()
                semaphore.signal()
            case .failed, .cancelled:
                semaphore.signal()
            default:
                break
            }
        }
        connection.start(queue: DispatchQueue.global(qos: .utility))
        _ = semaphore.wait(timeout: .now() + .milliseconds(700))
        connection.cancel()
        resultLock.lock()
        defer { resultLock.unlock() }
        return result
    }

    func isHTTPReady(_ url: URL) -> Bool {
        let semaphore = DispatchSemaphore(value: 0)
        var result = false
        var request = URLRequest(url: url)
        request.timeoutInterval = 2
        let task = URLSession.shared.dataTask(with: request) { _, response, _ in
            if let response = response as? HTTPURLResponse {
                result = response.statusCode < 500
            }
            semaphore.signal()
        }
        task.resume()
        _ = semaphore.wait(timeout: .now() + .seconds(3))
        task.cancel()
        return result
    }
}

private final class LauncherViewController: NSViewController {
    private let supervisor = ProcessSupervisor()
    private var services: [String: ServiceDefinition] = [:]
    private var statusLabels: [String: NSTextField] = [:]
    private var detailLabels: [String: NSTextField] = [:]
    private let logQueue = DispatchQueue(label: "com.xgls.ai-training-launcher.log")
    private let messageLabel = NSTextField(labelWithString: "准备就绪。点击“启动全部”开始运行平台。")
    private let startButton = NSButton(title: "启动全部", target: nil, action: nil)
    private let stopButton = NSButton(title: "停止全部", target: nil, action: nil)
    private let openButton = NSButton(title: "打开平台", target: nil, action: nil)
    private let autoOpenButton = NSButton(checkboxWithTitle: "启动完成后打开浏览器", target: nil, action: nil)
    private var busy = false
    private var workspace: URL?

    override func loadView() {
        view = NSView()
        view.translatesAutoresizingMaskIntoConstraints = false
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        workspace = findWorkspace()
        configureServices()
        buildView()
        refreshStatuses()
    }

    private func configureServices() {
        guard let root = workspace else { return }
        let logs = root.appendingPathComponent("logs/launcher", isDirectory: true)
        services["backend"] = ServiceDefinition(key: "backend", title: "后端 Spring Boot", port: 8081, healthURL: URL(string: "http://127.0.0.1:8081/"), command: "mvn -Dmaven.test.skip=true -f pom.xml spring-boot:run", workingDirectory: root.appendingPathComponent("backend"), logFile: logs.appendingPathComponent("backend.log"))
        services["frontend"] = ServiceDefinition(key: "frontend", title: "前端 Vite", port: 5173, healthURL: URL(string: "http://127.0.0.1:5173/dist/"), command: "npm run dev -- --host 127.0.0.1 --port 5173 --strictPort", workingDirectory: root.appendingPathComponent("fronternd"), logFile: logs.appendingPathComponent("frontend.log"))
        services["runner"] = ServiceDefinition(key: "runner", title: "Python Runner", port: 8009, healthURL: URL(string: "http://127.0.0.1:8009/health"), command: "bash start_runner.sh", workingDirectory: root.appendingPathComponent("engines/mmdet_run/mmdet_runner_srv"), logFile: logs.appendingPathComponent("runner.log"))
    }

    private func buildView() {
        let stack = NSStackView()
        stack.orientation = .vertical
        stack.spacing = 12
        stack.edgeInsets = NSEdgeInsets(top: 20, left: 20, bottom: 16, right: 20)
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor), stack.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            stack.topAnchor.constraint(equalTo: view.topAnchor), stack.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])

        let title = NSTextField(labelWithString: "AI 训练平台")
        title.font = .boldSystemFont(ofSize: 22)
        let subtitle = NSTextField(labelWithString: "macOS 启动器")
        subtitle.textColor = .secondaryLabelColor
        stack.addArrangedSubview(title)
        stack.addArrangedSubview(subtitle)

        let actions = NSStackView()
        actions.orientation = .horizontal
        actions.spacing = 8
        startButton.target = self; startButton.action = #selector(startAll)
        stopButton.target = self; stopButton.action = #selector(stopAll); stopButton.isEnabled = false
        openButton.target = self; openButton.action = #selector(openPlatform); openButton.isEnabled = false
        autoOpenButton.state = .on
        actions.addArrangedSubview(startButton); actions.addArrangedSubview(stopButton); actions.addArrangedSubview(openButton)
        actions.addArrangedSubview(NSView())
        actions.addArrangedSubview(autoOpenButton)
        stack.addArrangedSubview(actions)

        let statusBox = NSBox()
        statusBox.title = "服务状态"
        let statusStack = NSStackView()
        statusStack.orientation = .vertical
        statusStack.spacing = 7
        statusStack.edgeInsets = NSEdgeInsets(top: 8, left: 10, bottom: 10, right: 10)
        for (key, titleText, port) in [("mysql", "MySQL", 3306), ("backend", "后端 Spring Boot", 8081), ("runner", "Python Runner", 8009), ("frontend", "前端 Vite", 5173)] {
            let row = NSStackView()
            row.orientation = .horizontal
            row.spacing = 8
            let name = NSTextField(labelWithString: titleText); name.widthAnchor.constraint(equalToConstant: 140).isActive = true
            let status = NSTextField(labelWithString: "未检查"); status.widthAnchor.constraint(equalToConstant: 78).isActive = true
            let detail = NSTextField(labelWithString: "端口 \(port)"); detail.textColor = .secondaryLabelColor
            row.addArrangedSubview(name); row.addArrangedSubview(status); row.addArrangedSubview(detail); row.addArrangedSubview(NSView())
            statusStack.addArrangedSubview(row); statusLabels[key] = status; detailLabels[key] = detail
        }
        statusBox.contentView = statusStack
        stack.addArrangedSubview(statusBox)

        let footer = NSStackView()
        footer.orientation = .horizontal
        footer.spacing = 8
        messageLabel.textColor = .secondaryLabelColor
        footer.addArrangedSubview(messageLabel); footer.addArrangedSubview(NSView())
        let logsButton = NSButton(title: "打开启动日志", target: self, action: #selector(openLogs)); footer.addArrangedSubview(logsButton)
        stack.addArrangedSubview(footer)
    }

    private func findWorkspace() -> URL? {
        let fileManager = FileManager.default
        if let override = ProcessInfo.processInfo.environment[workspaceOverrideKey], fileManager.fileExists(atPath: override) { return URL(fileURLWithPath: override) }
        var candidate = Bundle.main.bundleURL.deletingLastPathComponent()
        for _ in 0..<4 {
            if fileManager.fileExists(atPath: candidate.appendingPathComponent("backend").path), fileManager.fileExists(atPath: candidate.appendingPathComponent("fronternd").path) { return candidate }
            candidate.deleteLastPathComponent()
        }
        return nil
    }

    private func environment() -> [String: String] {
        var env = ProcessInfo.processInfo.environment
        env["APP_WORKSPACE_ROOT"] = workspace?.path
        env["APP_REDIS_REQUIRED"] = env["APP_REDIS_REQUIRED"] ?? "false"
        env["APP_REDIS_FALLBACK_MEMORY"] = env["APP_REDIS_FALLBACK_MEMORY"] ?? "true"
        env["RUNNER_AUTO_START"] = "false"
        env["RUNNER_AUTO_STOP_ON_SHUTDOWN"] = "false"
        if let envURL = workspace?.appendingPathComponent("backend/.env.local"), let text = try? String(contentsOf: envURL, encoding: .utf8) {
            for line in text.split(separator: "\n") {
                let parts = line.split(separator: "=", maxSplits: 1).map(String.init)
                if parts.count == 2, !parts[0].trimmingCharacters(in: .whitespaces).isEmpty { env[parts[0].trimmingCharacters(in: .whitespaces)] = parts[1].trimmingCharacters(in: .whitespaces).trimmingCharacters(in: CharacterSet(charactersIn: "\"'")) }
            }
        }
        let java17Homes = [
            "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home",
            "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home",
        ]
        if let java17Home = java17Homes.first(where: { FileManager.default.isExecutableFile(atPath: "\($0)/bin/java") }) {
            env["JAVA_HOME"] = java17Home
        }
        let inheritedPath = env["PATH"] ?? "/usr/bin:/bin:/usr/sbin:/sbin"
        let javaBin = env["JAVA_HOME"].map { "\($0)/bin" }
        let home = env["HOME"] ?? FileManager.default.homeDirectoryForCurrentUser.path
        let condaHomes = ["miniconda3", "anaconda3", "miniforge3", "mambaforge"].map {
            "\(home)/\($0)"
        }
        let condaPaths = condaHomes.map { "\($0)/bin" } + ["/opt/homebrew/Caskroom/miniconda/base/bin"]
        if env["RUNNER_PYTHON"] == nil,
           let condaPython = condaHomes.map({ "\($0)/bin/python3" }).first(where: { FileManager.default.isExecutableFile(atPath: $0) }) {
            env["RUNNER_PYTHON"] = condaPython
        }
        env["PATH"] = ([javaBin] + condaPaths + ["/opt/homebrew/bin", "/usr/local/bin", inheritedPath]).compactMap { $0 }
            .joined(separator: ":")
        return env
    }

    private func javaMajorVersion(environment: [String: String]) -> Int? {
        let probe = Process()
        probe.executableURL = URL(fileURLWithPath: "/bin/zsh")
        probe.arguments = ["-c", "java -version 2>&1"]
        probe.environment = environment
        let output = Pipe()
        probe.standardOutput = output
        probe.standardError = output
        do {
            try probe.run()
            let text = String(data: output.fileHandleForReading.readDataToEndOfFile(), encoding: .utf8) ?? ""
            probe.waitUntilExit()
            guard let range = text.range(of: "version ") else { return nil }
            let version = text[range.upperBound...].trimmingCharacters(in: CharacterSet(charactersIn: "\" \n\r\t"))
            guard let digits = version.split(separator: ".").first else { return nil }
            return Int(digits)
        } catch {
            return nil
        }
    }

    private func commandAvailable(_ command: String, environment: [String: String]) -> Bool {
        let probe = Process()
        probe.executableURL = URL(fileURLWithPath: "/bin/zsh")
        probe.arguments = ["-c", "command -v \(command) >/dev/null 2>&1"]
        probe.environment = environment
        probe.standardOutput = FileHandle.nullDevice
        probe.standardError = FileHandle.nullDevice
        do {
            try probe.run()
            probe.waitUntilExit()
            return probe.terminationStatus == 0
        } catch {
            return false
        }
    }

    private func installFrontendDependencies(environment: [String: String]) -> Bool {
        guard let root = workspace else { return false }
        let logURL = root.appendingPathComponent("logs/launcher/frontend-install.log")
        do {
            try FileManager.default.createDirectory(at: logURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            FileManager.default.createFile(atPath: logURL.path, contents: nil)
            let logHandle = try FileHandle(forWritingTo: logURL)
            let process = Process()
            process.executableURL = URL(fileURLWithPath: "/bin/zsh")
            process.arguments = ["-c", "npm ci --no-audit --no-fund"]
            process.currentDirectoryURL = root.appendingPathComponent("fronternd")
            process.environment = environment
            process.standardOutput = logHandle
            process.standardError = logHandle
            try process.run()
            process.waitUntilExit()
            try? logHandle.close()
            return process.terminationStatus == 0
        } catch {
            appendLog("前端依赖安装异常：\(error)")
            return false
        }
    }

    @objc private func startAll() {
        guard !busy else { return }
        guard workspace != nil, services["backend"] != nil else { showError("没有找到项目根目录，请把启动器放在项目根目录内。") ; return }
        busy = true; startButton.isEnabled = false; stopButton.isEnabled = false
        setMessage("正在检查环境和服务。")
        appendLog("开始启动 macOS 服务。")
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in self?.startWorker() }
    }

    private func startWorker() {
        guard let backend = services["backend"], let runner = services["runner"], let frontend = services["frontend"] else { return }
        let serviceEnvironment = environment()
        guard javaMajorVersion(environment: serviceEnvironment) == 17 else {
            finish(false, message: "需要使用 JDK 17，请安装 JDK 17 后重试。", log: "Java 版本不匹配：项目需要 JDK 17。")
            return
        }
        for (command, title) in [("java", "JDK"), ("mvn", "Maven"), ("npm", "Node.js")] {
            guard commandAvailable(command, environment: serviceEnvironment) else {
                finish(false, message: "未找到\(title)，请先完成安装。", log: "环境检查失败：未找到 \(command)。")
                return
            }
        }
        if let root = workspace, !FileManager.default.fileExists(atPath: root.appendingPathComponent("fronternd/node_modules").path) {
            setMessage("正在安装前端依赖，请稍候。")
            appendLog("首次启动：正在执行 npm ci。")
            guard installFrontendDependencies(environment: serviceEnvironment) else {
                finish(false, message: "前端依赖安装失败，请查看日志。", log: "npm ci 执行失败。")
                return
            }
            appendLog("前端依赖安装完成。")
        }
        if !supervisor.isPortOpen(3306) { finish(false, message: "MySQL 未运行，请先启动 MySQL 服务。", log: "未检测到 MySQL 3306。") ; return }
        let backendProcess: Process?
        let runnerProcess: Process?
        let frontendProcess: Process?
        do {
            backendProcess = try supervisor.start(backend, environment: serviceEnvironment, onOutput: { [weak self] text in self?.appendLog(text) }, onExit: { [weak self] code in self?.appendLog("后端进程退出，状态码 \(code)。") })
            runnerProcess = try supervisor.start(runner, environment: serviceEnvironment, onOutput: { [weak self] text in self?.appendLog(text) }, onExit: { [weak self] code in self?.appendLog("Runner 进程退出，状态码 \(code)。") })
            frontendProcess = try supervisor.start(frontend, environment: serviceEnvironment, onOutput: { [weak self] text in self?.appendLog(text) }, onExit: { [weak self] code in self?.appendLog("前端进程退出，状态码 \(code)。") })
        } catch {
            finish(false, message: "服务启动失败：\(error.localizedDescription)", log: "服务启动异常：\(error)")
            return
        }

        guard waitUntil({ self.supervisor.isPortOpen(8081) }, process: backendProcess, seconds: 120) else {
            let exited = backendProcess.map { !$0.isRunning } ?? false
            if exited { setStatus("backend", "启动失败", "请查看 logs/launcher/backend.log") }
            finish(false, message: exited ? "后端启动失败，请查看日志。" : "后端启动超时，请查看日志。", log: exited ? "后端进程已退出，未监听 8081。" : "等待 8081 超时。")
            return
        }
        setStatus("backend", "正常", "http://127.0.0.1:8081")

        guard waitForRunner(seconds: 90, process: runnerProcess) else {
            let exited = runnerProcess.map { !$0.isRunning } ?? false
            setStatus("runner", exited ? "启动失败" : "超时", "logs/launcher/runner.log")
            finish(false, message: exited ? "Python Runner 启动失败，请查看日志。" : "Python Runner 启动超时，请查看日志。", log: exited ? "Runner 进程已退出，未监听 8009。" : "Runner 在 90 秒内未通过健康检查。")
            return
        }
        setStatus("runner", "正常", "http://127.0.0.1:8009/health")

        guard waitUntil({ self.supervisor.isHTTPReady(URL(string: "http://127.0.0.1:5173/dist/")!) }, process: frontendProcess, seconds: 60) else {
            let exited = frontendProcess.map { !$0.isRunning } ?? false
            if exited { setStatus("frontend", "启动失败", "请查看 logs/launcher/frontend.log") }
            finish(false, message: exited ? "前端启动失败，请查看日志。" : "前端启动超时，请查看日志。", log: exited ? "前端进程已退出，未监听 5173。" : "等待 5173 超时。")
            return
        }
        setStatus("frontend", "正常", "http://127.0.0.1:5173/dist/")
        let shouldOpenBrowser = autoOpenButton.state == .on
        finish(true, message: "平台已启动，可以开始使用。", log: "全部服务已就绪。")
        if shouldOpenBrowser {
            DispatchQueue.main.async {
                NSWorkspace.shared.open(URL(string: "http://127.0.0.1:5173/dist/")!)
            }
        }
    }

    private func waitUntil(_ predicate: @escaping () -> Bool, process: Process?, seconds: Int) -> Bool {
        let deadline = Date().addingTimeInterval(TimeInterval(seconds))
        while Date() < deadline {
            if predicate() { return true }
            if let process, !process.isRunning { return false }
            Thread.sleep(forTimeInterval: 0.5)
        }
        return false
    }

    private func waitForRunner(seconds: Int, process: Process?) -> Bool {
        let healthURL = URL(string: "http://127.0.0.1:8009/health")!
        let deadline = Date().addingTimeInterval(TimeInterval(seconds))
        while Date() < deadline {
            if supervisor.isHTTPReady(healthURL) { return true }
            if let process, !process.isRunning { return false }
            Thread.sleep(forTimeInterval: 0.5)
        }
        return false
    }

    @objc private func stopAll() {
        guard !busy else { return }
        busy = true
        startButton.isEnabled = false
        stopButton.isEnabled = false
        openButton.isEnabled = false
        setMessage("正在停止本次启动器创建的服务。")
        let processes = supervisor.stopAll { [weak self] text in self?.appendLog(text) }
        for key in processes.keys { setStatus(key, "正在停止", "等待进程退出") }
        DispatchQueue.global(qos: .utility).async { [weak self] in
            let deadline = Date().addingTimeInterval(15)
            while Date() < deadline && processes.values.contains(where: \.isRunning) {
                Thread.sleep(forTimeInterval: 0.2)
            }
            guard let self else { return }
            let portByKey: [String: UInt16] = ["mysql": 3306, "backend": 8081, "runner": 8009, "frontend": 5173]
            let running = portByKey.mapValues { self.supervisor.isPortOpen($0) }
            DispatchQueue.main.async {
                for (key, port) in portByKey {
                    let isRunning = running[key] ?? false
                    let detail = key == "mysql"
                        ? (isRunning ? "由系统管理，停止全部不会关闭它" : "端口 \(port)")
                        : (isRunning ? "端口仍在响应，可能由其他程序占用" : "端口 \(port)")
                    self.setStatus(key, isRunning ? (key == "mysql" ? "系统运行中" : "仍在运行") : "已停止", detail)
                }
                self.busy = false
                self.startButton.isEnabled = true
                self.stopButton.isEnabled = self.supervisor.hasManagedProcesses
                self.openButton.isEnabled = false
                self.setMessage(running.contains(where: { $0.key != "mysql" && $0.value })
                    ? "部分服务仍在退出，请再次点击停止。"
                    : "本次启动器创建的服务已停止。")
            }
        }
    }
    func stopServices() { _ = supervisor.stopAll { _ in } }
    @objc private func openPlatform() { NSWorkspace.shared.open(URL(string: "http://127.0.0.1:5173/dist/")!) }
    @objc private func openLogs() {
        guard let root = workspace else { return }
        let logURL = root.appendingPathComponent("logs/launcher/launcher.log")
        do {
            try FileManager.default.createDirectory(at: logURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            if !FileManager.default.fileExists(atPath: logURL.path) {
                FileManager.default.createFile(atPath: logURL.path, contents: nil)
            }
            NSWorkspace.shared.open(logURL)
        } catch {
            showError("无法打开启动日志：\(error.localizedDescription)")
        }
    }

    private func finish(_ success: Bool, message: String, log: String) { DispatchQueue.main.async { [weak self] in guard let self else { return }; self.busy = false; self.startButton.isEnabled = !success; self.stopButton.isEnabled = self.supervisor.hasManagedProcesses; self.openButton.isEnabled = success; self.setMessage(message); self.appendLog(log) } }
    private func setMessage(_ text: String) { DispatchQueue.main.async { self.messageLabel.stringValue = text } }
    private func setStatus(_ key: String, _ status: String, _ detail: String) { DispatchQueue.main.async { self.statusLabels[key]?.stringValue = status; self.detailLabels[key]?.stringValue = detail } }
    private func appendLog(_ text: String) {
        guard let root = workspace else { return }
        let logURL = root.appendingPathComponent("logs/launcher/launcher.log")
        let line = "\(DateFormatter.localizedString(from: Date(), dateStyle: .none, timeStyle: .medium))  \(text)\n"
        logQueue.async {
            do {
                try FileManager.default.createDirectory(at: logURL.deletingLastPathComponent(), withIntermediateDirectories: true)
                if !FileManager.default.fileExists(atPath: logURL.path) {
                    FileManager.default.createFile(atPath: logURL.path, contents: nil)
                }
                let handle = try FileHandle(forWritingTo: logURL)
                try handle.seekToEnd()
                try handle.write(contentsOf: Data(line.utf8))
                try handle.close()
            } catch {
                NSLog("Unable to write launcher log: %@", error.localizedDescription)
            }
        }
    }
    private func refreshStatuses() {
        DispatchQueue.global(qos: .utility).async { [weak self] in
            guard let self else { return }
            let mysqlRunning = self.supervisor.isPortOpen(3306)
            let backendRunning = self.supervisor.isPortOpen(8081)
            let frontendRunning = self.supervisor.isPortOpen(5173)
            let runnerRunning = self.supervisor.isHTTPReady(URL(string: "http://127.0.0.1:8009/health")!)
            self.setStatus("mysql", mysqlRunning ? "正常" : "未运行", mysqlRunning ? "由系统服务管理" : "端口 3306")
            self.setStatus("backend", backendRunning ? "正常" : "未启动", "端口 8081")
            self.setStatus("frontend", frontendRunning ? "正常" : "未启动", "端口 5173")
            self.setStatus("runner", runnerRunning ? "正常" : "未启动", "端口 8009")
        }
    }
    private func showError(_ text: String) { NSAlert(error: NSError(domain: "AITrainingPlatformLauncher", code: 1, userInfo: [NSLocalizedDescriptionKey: text])).runModal() }
}

private final class AppDelegate: NSObject, NSApplicationDelegate {
    var window: NSWindow!
    private var controller: LauncherViewController?
    func applicationDidFinishLaunching(_ notification: Notification) {
        let controller = LauncherViewController()
        self.controller = controller
        window = NSWindow(contentViewController: controller)
        window.title = "AI 训练平台启动器（macOS）"
        window.setContentSize(NSSize(width: 820, height: 380))
        window.center(); window.makeKeyAndOrderFront(nil)
        NSApp.activate(ignoringOtherApps: true)
    }
    func applicationWillTerminate(_ notification: Notification) { controller?.stopServices() }
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
}

let application = NSApplication.shared
private let delegate = AppDelegate()
application.delegate = delegate
application.setActivationPolicy(.regular)
application.run()
