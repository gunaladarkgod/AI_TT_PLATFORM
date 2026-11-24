import unittest
from unittest.mock import patch

from proxy_environment import apply_proxy_environment


class ProxyEnvironmentTest(unittest.TestCase):
    @patch("proxy_environment.subprocess.run")
    def test_uses_github_git_proxy_for_python_worker(self, run):
        run.return_value.returncode = 0
        run.return_value.stdout = "http://127.0.0.1:7897\n"
        env = apply_proxy_environment({})
        for key in ("HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "http_proxy", "https_proxy", "all_proxy"):
            self.assertEqual(env[key], "http://127.0.0.1:7897")

    @patch("proxy_environment.subprocess.run")
    def test_explicit_proxy_environment_takes_precedence(self, run):
        run.return_value.returncode = 0
        run.return_value.stdout = "http://127.0.0.1:7897\n"
        env = apply_proxy_environment({"HTTPS_PROXY": "http://explicit-proxy:8080"})
        self.assertEqual(env["HTTPS_PROXY"], "http://explicit-proxy:8080")
        self.assertEqual(env["HTTP_PROXY"], "http://127.0.0.1:7897")

    @patch("proxy_environment.subprocess.run")
    def test_without_git_proxy_leaves_environment_unchanged(self, run):
        run.return_value.returncode = 1
        env = {"KEEP": "value"}
        self.assertEqual(apply_proxy_environment(env), env)
        self.assertEqual(env, {"KEEP": "value"})


if __name__ == "__main__":
    unittest.main()
