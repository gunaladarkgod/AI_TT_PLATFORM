package com.xgls.web.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xgls.web.base.CodeMap;
import com.xgls.web.entity.TrainScript;
import com.xgls.web.mapper.TrainScriptMapper;
import com.xgls.web.service.TrainScriptService;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;

@Service
public class TrainScriptService extends ServiceImpl<TrainScriptMapper, TrainScript> {
  @Value("${sys.conda}")
  String condaPath;
  @Value("${sys.root-upload}")
  String rootPath;

  @Transactional
  public boolean saveLink(TrainScript record) throws IOException {
    if (baseMapper.insert(record) <= 0) {
      return false;
    }
    String type = record.getType();
    String filePath = record.getId() + record.getSuff();
    boolean iswin = FileUtil.isWindows();
    String script = "";
    if (StrUtil.equals(type, CodeMap.SCRIPT_TYPE_TRAIN)) {
      // 训练脚本
      script = iswin ? buildScriptWin(condaPath, record) : buildScriptLinux(condaPath, record);
    } else if (StrUtil.equals(CodeMap.SCRIPT_TYPE_TRANS, type)) {
      // 模型转换脚本
      script = iswin ? buildNormalWin(condaPath, record) : buildNormalLinux(condaPath, record);
    } else if (StrUtil.equals(CodeMap.SCRIPT_TYPE_DATA, type)) {
      // 数据转换脚本
      script = iswin ? buildNormalWinOffset(condaPath, record, 4) : buildNormalLinuxOffset(condaPath, record, 4);
    }
    Files.write(Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_SCRIPT, filePath), script.getBytes());
    return true;
  }

  public boolean updateByIdLink(TrainScript record) throws IOException {
    if (baseMapper.updateById(record) <= 0) {
      return false;
    }
    String filePath = record.getId() + record.getSuff();
    String type = record.getType();
    String script = "";
    boolean iswin = FileUtil.isWindows();
    if (StrUtil.equals(CodeMap.SCRIPT_TYPE_TRAIN, type)) {
      script = iswin ? buildScriptWin(condaPath, record) : buildScriptLinux(condaPath, record);
    } else if (StrUtil.equals(CodeMap.SCRIPT_TYPE_TRANS, type)) {
      script = iswin ? buildNormalWin(condaPath, record) : buildNormalLinux(condaPath, record);
    } else if (StrUtil.equals(CodeMap.SCRIPT_TYPE_DATA, type)) {
      script = iswin ? buildNormalWinOffset(condaPath, record, 4) : buildNormalLinuxOffset(condaPath, record, 4);
    }
    Files.write(Paths.get(rootPath, CodeMap.DIR_SRC, CodeMap.DIR_SCRIPT, filePath), script.getBytes());
    return true;
  }

  /** linux 训练脚本 */
  private String buildScriptLinux(String condaPath, TrainScript record) {
    switch (record.getCmd()) {
      case "python":
        return String.format("""
            #!/bin/bash
            . %s
            conda activate %s
            python -V

            if [ -z "$1" ]; then
                echo "Error: TRAIN_TASK_ID is required as the first argument."
                exit 1
            fi
            TRAIN_TASK_ID=$1
            echo "TRAIN_TASK_ID is set to: $TRAIN_TASK_ID"
            export TRAIN_TASK_ID=$1
            # check NODE
            if [ -z "$2" ]; then
                echo "Error: node is required for additional training parameters."
                exit 1
            fi
            NODE=$2
            echo "NODE is set to: $NODE"

            if [ -z "$3" ]; then
                echo "Error: cmd is required for additional training parameters."
                exit 1
            fi
            MY_CMD=$3
            echo "MY_CMD is set to: $MY_CMD"

            params=""
            for arg in "${@:4}"; do
                params="$params $arg"
            done

            echo "All-LEFT arguments: $params"
            if [ -z "$params" ]; then
                echo "Error: No additional arguments provided for training."
                exit 1
            fi

            if [ "$NODE" == "1" ]; then
                echo "NODE is equal to '1'."
                python $params
            else
                echo "NODE is not equal to '1', NODE is $NODE."
                python -m torch.distributed.run --nproc_per_node=$NODE  $params
            fi

            """, condaPath, record.getEnv());
      case "yolo":
        return String.format("""
            #!/bin/bash
            . %s
            conda activate %s
            python -V

            if [ -z "$1" ]; then
                echo "Error: TRAIN_TASK_ID is required as the first argument."
                exit 1
            fi
            TRAIN_TASK_ID=$1
            echo "TRAIN_TASK_ID is set to: $TRAIN_TASK_ID"
            export TRAIN_TASK_ID=$1
            # check NODE
            if [ -z "$2" ]; then
                echo "Error: node is required for additional training parameters."
                exit 1
            fi
            NODE=$2
            echo "NODE is set to: $NODE"

            if [ -z "$3" ]; then
                echo "Error: cmd is required for additional training parameters."
                exit 1
            fi
            MY_CMD=$3
            echo "MY_CMD is set to: $MY_CMD"

            params=""
            for arg in "${@:4}"; do
                params="$params $arg"
            done

            echo "All-LEFT arguments: $params"
            if [ -z "$params" ]; then
                echo "Error: No additional arguments provided for training."
                exit 1
            fi

            yolo  $params
            """, condaPath, record.getEnv());
      case "mmdet":
        return String.format("""
            #!/bin/bash
            . %s
            conda activate %s
            python -V

            APP_TRAIN_DIR=%s
            APP_TASK_DIR=%s
            MAIN_SCRIPT=%s

            export APP_TRAIN_DIR
            export APP_TASK_DIR
            export MAIN_SCRIPT

            count=0

            for arg in "$@"; do
              ((count++))

              case $count in
                1)
                  export TRAIN_TASK_ID=$arg
                  export CFG_PATH="$APP_TRAIN_DIR/$TRAIN_TASK_ID/file/cfg.py"
                  echo "TRAIN_TASK_ID: $TRAIN_TASK_ID"
                  echo "CFG_PATH: $CFG_PATH"
                  ;;
                6)
                  export MMDET_WEIGHTS=$arg
                  echo "MMDET_WEIGHTS: $MMDET_WEIGHTS"
                  ;;
                8)
                  export MMDET_DEVICE=$arg
                  echo "MMDET_DEVICE: $MMDET_DEVICE"
                  ;;
                10)
                  export MMDET_BATCH_SIZE=$arg
                  echo "MMDET_BATCH_SIZE: $MMDET_BATCH_SIZE"
                  ;;
                12)
                  export MMDET_IMAGE_WIDTH=$arg
                  echo "MMDET_IMAGE_WIDTH: $MMDET_IMAGE_WIDTH"
                  ;;
                14)
                  export MMDET_IMAGE_HEIGHT=$arg
                  echo "MMDET_IMAGE_HEIGHT: $MMDET_IMAGE_HEIGHT"
                  ;;
                16)
                  export MMDET_EPOCH=$arg
                  echo "MMDET_EPOCH: $MMDET_EPOCH"
                  ;;
                18)
                  export MMDET_SAVE_PERIOD=$arg
                  echo "MMDET_SAVE_PERIOD: $MMDET_SAVE_PERIOD"
                  ;;
                20)
                  export MMDET_WORK_DIR=$arg
                  echo "MMDET_WORK_DIR: $MMDET_WORK_DIR"
                  ;;
                22)
                  export MMDET_NUM_CLASSES=$arg
                  echo "MMDET_NUM_CLASSES: $MMDET_NUM_CLASSES"
                  ;;
                24)
                  export MMDET_CLASSES=$arg
                  echo "MMDET_CLASSES: $MMDET_CLASSES"
                  ;;
              esac
            done

            python $MAIN_SCRIPT $CFG_PATH
            """, condaPath.replace("\\", "/"), record.getEnv(),
            rootPath.replace("\\", "/") + CodeMap.DIR_SRC + "/" + CodeMap.DIR_TRAIN_TASK + "/",
            rootPath.replace("\\", "/") + CodeMap.DIR_SRC + "/" + CodeMap.DIR_CVAT_TASK + "/",
            record.getMain().replace("\\", "/"));
      default:
        return "echo NOT SPORT CMD TYPE";
    }
  }

  /** windows 训练脚本 */
  private String buildScriptWin(String condaPath, TrainScript record) {
    switch (record.getCmd()) {
      case "python":
        return String.format("""
            @echo off
            setlocal enabledelayedexpansion

            CALL %s activate %s
            python -V

            if "%%1"=="" (
              echo Error: TRAIN_TASK_ID is required as the first argument.
              exit /b 1
            )

            set TRAIN_TASK_ID=%%1
            echo TRAIN_TASK_ID is set to: %%1

            if "%%2"=="" (
              echo Error: node  is required for additional training parameters.
              exit /b 1
            )
            set NODE=%%2
            echo NODE is set to: %%2

            if "%%3"=="" (
              echo Error: cmd  is required for additional training parameters.
              exit /b 1
            )

            set count=0
            set params=
            for %%%%A in (%%*) do (
              set /a count+=1
              if !count! GTR 3 (
                set param=%%%%A
                set params=!params! "!param:::==!"
              )
            )

            echo All-LEFT arguments:  !params!
            if "!params!"=="" (
              echo Error: No additional arguments provided for training.
              exit /b 1
            )

            if "%%NODE%%"=="1" (
              echo NODE is equal to "1".
              python !params!
            ) else (
              echo NODE is not equal to "1", NODE is %%NODE%%.
              python  -m torch.distributed.run --nproc_per_node %%NODE%%  !params!
            )
            endlocal
            """, condaPath, record.getEnv());
      case "yolo":
        return String.format("""
            @echo off
            setlocal enabledelayedexpansion

            CALL %s activate %s
            python -V

            if "%%1"=="" (
              echo Error: TRAIN_TASK_ID is required as the first argument.
              exit /b 1
            )

            set TRAIN_TASK_ID=%%1
            echo TRAIN_TASK_ID is set to: %%1

            if "%%2"=="" (
              echo Error: node  is required for additional training parameters.
              exit /b 1
            )
            set NODE=%%2
            echo NODE is set to: %%2

            if "%%3"=="" (
              echo Error: cmd  is required for additional training parameters.
              exit /b 1
            )

            set count=0
            set params=
            for %%%%A in (%%*) do (
              set /a count+=1
              if !count! GTR 3 (
                set param=%%%%A
                set params=!params! "!param:::==!"
              )
            )

            echo All-LEFT arguments:  !params!
            if "!params!"=="" (
              echo Error: No additional arguments provided for training.
              exit /b 1
            )
            yolo  !params!

            endlocal
            """, condaPath, record.getEnv());
      case "mmdet":
        return String.format("""
            @echo off
            setlocal enabledelayedexpansion

            CALL %s activate %s
            python -V

            set APP_TRAIN_DIR=%s
            set APP_TASK_DIR=%s
            set MAIN_SCRIPT=%s

            set count=0
            for %%%%A in (%%*) do (
              set /a count+=1
              if !count! == 1 (
                echo !count!:%%%%A
                set TRAIN_TASK_ID=%%%%A
                set CFG_PATH=%%APP_TRAIN_DIR%%%%%%A/file/cfg.py
                echo CFG_PATH:!CFG_PATH!
              )
              if !count! == 6 (
                echo !count!:%%%%A
                set MMDET_WEIGHTS=%%%%A
              )
              if !count! == 8 (
                echo !count!:%%%%A
                set MMDET_DEVICE=%%%%A
              )
              if !count! == 10 (
                echo !count!:%%%%A
                set MMDET_BATCH_SIZE=%%%%A
              )
              if !count! == 12 (
                echo !count!:%%%%A
                set MMDET_IMAGE_WIDTH=%%%%A
              )
              if !count! == 14 (
                echo !count!:%%%%A
                set MMDET_IMAGE_HEIGHT=%%%%A
              )
              if !count! == 16 (
                echo !count!:%%%%A
                set MMDET_EPOCH=%%%%A
              )
              if !count! == 18 (
                echo !count!:%%%%A
                set MMDET_SAVE_PERIOD=%%%%A
              )
              if !count! == 20 (
                echo !count!:%%%%A
                set MMDET_WORK_DIR=%%%%A
              )
              if !count! == 22 (
                echo !count!:%%%%A
                set MMDET_NUM_CLASSES=%%%%A
              )
              if !count! == 24 (
                echo !count!:%%%%A
                set MMDET_CLASSES=%%%%A
              )
            )
            python %%MAIN_SCRIPT%% !CFG_PATH!
            endlocal
            """, condaPath.replace("\\", "/"), record.getEnv(),
            rootPath.replace("\\", "/") + CodeMap.DIR_SRC + "/" + CodeMap.DIR_TRAIN_TASK + "/",
            rootPath.replace("\\", "/") + CodeMap.DIR_SRC + "/" + CodeMap.DIR_CVAT_TASK + "/",
            record.getMain().replace("\\", "/"));
      // 转义是因为mmdet配置文件避免\出错
      default:
        return "echo NOT SPORT CMD TYPE";
    }
  }

  /** linux 通用脚本 */
  private String buildNormalLinux(String condaPath, TrainScript record) {
    // python3 ./export.py
    String cmd = record.getCmd();
    String script = String.format("""
        #!/bin/bash
        . %s
        conda activate %s
        python -V
        %s %s "$@"
        """, condaPath, record.getEnv(), cmd, record.getMain());
    return script;
  }

  /** linux 通用脚本 舍弃部分参数 */
  private String buildNormalLinuxOffset(String condaPath, TrainScript record, Integer n) {
    // python3 ./export.py
    String cmd = record.getCmd();
    String script = String.format("""
        #!/bin/bash
        . %s
        conda activate %s
        python -V

        params=""
        for arg in "${@:%d}"; do
            params="$params $arg"
        done

        %s %s $params
        """, condaPath, record.getEnv(), n + 1, cmd, record.getMain());
    return script;
  }

  /** windows 通用脚本 */
  private String buildNormalWin(String condaPath, TrainScript record) {
    String cmd = record.getCmd();
    if (StrUtil.equals(cmd, ".")) {
      cmd = "CALL";
    }
    String script = String.format("""
        @echo off
        CALL %s activate %s
        python -V
        %s %s %%*
        """, condaPath, record.getEnv(), cmd, record.getMain());
    return script;
  }

  /** windows 通用脚本,舍弃部分参数 */
  private String buildNormalWinOffset(String condaPath, TrainScript record, Integer n) {
    String cmd = record.getCmd();
    if (StrUtil.equals(cmd, ".")) {
      cmd = "CALL";
    }
    String script = String.format("""
        @echo off
        setlocal enabledelayedexpansion
        CALL %s activate %s
        python -V

        set count=0
        set params=
        for %%%%A in (%%*) do (
          set /a count+=1
          if !count! GTR %d (
            set param=%%%%A
            set params=!params! "!param:::==!"
          )
        )

        %s %s !params!
        endlocal
        """, condaPath, record.getEnv(), n, cmd, record.getMain());
    return script;
  }
}
