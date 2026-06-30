#!/bin/bash
#
# Copyright 2020 WeBank
#
# Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Launcher for modules, provided start/stop functions

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
ENV_FILE="${DIR}/env.properties"
SHELL_LOG="${DIR}/command.log"
USER_DIR="${DIR}/../"
EXCHANGIS_LIB_PATH="${DIR}/../lib"
EXCHANGIS_PID_PATH="${DIR}/../runtime"
# Default
MAIN_CLASS=""
DEBUG_MODE=False
DEBUG_PORT="7006"
SPRING_PROFILE="exchangis"
SLEEP_TIMEREVAL_S=2

function LOG(){
  currentTime=`date "+%Y-%m-%d %H:%M:%S.%3N"`
  echo -e "$currentTime [${1}] ($$) $2" | tee -a ${SHELL_LOG}
}

abs_path(){
    SOURCE="${BASH_SOURCE[0]}"
    while [ -h "${SOURCE}" ]; do
        DIR="$( cd -P "$( dirname "$SOURCE" )" && pwd )"
        SOURCE="$(readlink "${SOURCE}")"
        [[ ${SOURCE} != /* ]] && SOURCE="${DIR}/${SOURCE}"
    done
    echo "$( cd -P "$( dirname "${SOURCE}" )" && pwd )"
}

verify_java_env(){
    if [[ "x${JAVA_HOME}" != "x" ]]; then
      ${JAVA_HOME}/bin/java -version >/dev/null 2>&1
    else
      java -version >/dev/null 2>&1
    fi
    if [[ $? -ne 0 ]]; then
        cat 1>&2 <<EOF
+========================================================================+
| Error: Java Environment is not availiable, Please check your JAVA_HOME |
+------------------------------------------------------------------------+
EOF
        exit 1
    else
        return 0
    fi
}

# load environment definition
load_env_definitions(){
   if [[ -f "$1" ]]; then
      LOG INFO "load environment properties"
      while read line
      do
        if [[ ! -z $(echo "${line}" | grep "=") ]]; then
          local key=${line%%=*}
          local value=${line##*=}
          local key1=$(echo ${key} | tr '.' '_')
          if [[ -z $(echo "${key1}" | grep -P '\s*#+.*') ]]; then
            eval "${key1}=${value}"
          fi
        fi
      done < "${ENV_FILE}"
   fi
   if [[ "x${JAVA_HOME}" != "x" ]]; then
      JPS=${JAVA_HOME}/bin/jps
   else
      EXE_JAVA="java "${JAVA_OPTS}" "${MAIN_CLASS}
      JPS="jps"
  fi
}

construct_java_command(){
    verify_java_env
    if [[ "x${EXCHANGIS_CONF_PATH}" == "x" ]]; then
        LOG ERROR "Prop:EXCHANGIS_CONF_PATH is missing, must be not empty or blank"
        exit 1
    fi
    if [[ "x${EXCHANGIS_LIB_PATH}" == "x" ]]; then
        LOG ERROR "Prop:EXCHANGIS_LIB_PATH is missing, must be not empty or blank"
        exit 1
    fi
    if [[ "x${EXCHANGIS_LOG_PATH}" == "x" ]]; then
        LOG ERROR "Prop:EXCHANGIS_LOG_PATH is missing, must be not empty or blank"
        exit 1
    fi
    if [[ "x$2" == "x" ]]; then
        LOG ERROR "Prop:MAIN_CLASS is missing, must be not empty or blank"
        exit 1
    fi
    # mkdir
    mkdir -p ${EXCHANGIS_LOG_PATH}
    mkdir -p ${EXCHANGIS_PID_PATH}
    local classpath=${EXCHANGIS_CONF_PATH}":."
    local opts=""
    classpath=${EXCHANGIS_LIB_PATH}/exchangis-server/*":"${classpath}
    LOG INFO "classpath:"${classpath}
    if [[ "x${EXCHANGIS_JAVA_OPTS}" == "x" ]]; then
      # Use G1 garbage collector
       local opts="-Xmx${SERVER_XMX} -Xms${SERVER_XMS} -XX:NewSize=${SERVER_NEW_SIZE} -XX:MaxNewSize=${SERVER_MAX_NEW_SIZE} -XX:PermSize=${SERVER_PERM_SIZE} -XX:MaxPermSize=${SERVER_MAX_PERM_SIZE}"
    fi
    opts=${opts}" -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8 -Xloggc:${EXCHANGIS_LOG_PATH}/$1-gc.log"
    if [[ "x${DEBUG_MODE}" == "xtrue" ]]; then
        if [[ "x${DEBUG_PORT}" == "x" ]]; then
            LOG ERROR  "Prop:DEBUG_PORT is missing, must be a number and not blank"
            exit 1
        fi
       opts=${opts}" -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=${DEBUG_PORT}"
    fi
    opts=${opts}" -XX:HeapDumpPath=${EXCHANGIS_LOG_PATH}/HeapDump/$1"
    if [[ "x"${SPRING_PROFILE} != "x" ]]; then
        opts=${opts}" -Dspring.profiles.active=${SPRING_PROFILE}"
    fi
    opts=${opts}" -DserviceName=$1 -Dlog.path=${EXCHANGIS_LOG_PATH} -Dpid.file=${EXCHANGIS_PID_PATH}/$1.pid"
    opts=${opts}" -Dlogging.level.reactor.ipc.netty.channel.CloseableContextHandler=off"
    opts=${opts}" -Duser.dir=${USER_DIR}"
    opts=${opts}" -classpath "${classpath}
    echo "opts:"${opts}
    if [[ "x${JAVA_HOME}" != "x" ]]; then
        EXEC_JAVA=${JAVA_HOME}"/bin/java "${opts}" "$2
    else
        EXEC_JAVA="java "${opts}" "$2
    fi
}

# check if the process still in jvm
status_class(){
    local p=""
    local pid_file_path=${EXCHANGIS_PID_PATH}/$1.pid
    if [ "x"${pid_file_path} != "x" ]; then
      if [ -f ${pid_file_path} ]; then
        local pid_in_file=`cat ${pid_file_path} 2>/dev/null`
        if [ "x"${pid_in_file} !=  "x" ]; then
          p=`${JPS} -q | grep ${pid_in_file} | awk '{print $1}'`
        fi
      fi
    else
      p=`${JPS} -l | grep "$2" | awk '{print $1}'`
    fi
    if [ -n "$p" ]; then
        # echo "$1 ($2) is still running with pid $p"
        return 0
    else
        # echo "$1 ($2) does not appear in the java process table"
        return 1
    fi
}

wait_for_startup(){
    local now_s=`date '+%s'`
    local stop_s=$((${now_s} + $1))
    while [ ${now_s} -le ${stop_s} ];do
        status_class $2 $3
        if [ $? -eq 0 ]; then
            return 0
        fi
        sleep ${SLEEP_TIMEREVAL_S}
        now_s=`date '+%s'`  #计算当前时间时间戳
    done
    return 1
}

wait_for_stop(){
    local now_s=`date '+%s'`
    local stop_s=$((${now_s} + $1))
    while [ ${now_s} -le ${stop_s} ];do
        status_class $2 $3
        if [ $? -eq 1 ]; then
            return 0
        fi
        sleep ${SLEEP_TIMEREVAL_S}
        now_s=`date '+%s'`
    done
    return 1
}

# Resolve the listening TCP port of a running process (best effort)
# Input: $1 = pid
# Output: prints the first listening TCP port (empty if not found / undetectable)
get_port_by_pid(){
    local pid=$1
    if [[ -z "$pid" ]]; then return; fi
    local port=""
    # lsof works on both Linux and macOS; NAME column looks like "*:9321"
    if command -v lsof >/dev/null 2>&1; then
        port=$(lsof -nP -iTCP -sTCP:LISTEN -a -p "$pid" 2>/dev/null | awk 'NR>1{print $9}' | sed 's/.*://' | head -1)
    fi
    # Fallback: netstat -tlnp (Linux only, needs the pid column)
    if [[ -z "$port" ]] && command -v netstat >/dev/null 2>&1; then
        port=$(netstat -tlnp 2>/dev/null | awk -v pid="/$pid/" '$0 ~ pid {n=split($4,a,":"); print a[n]}' | head -1)
    fi
    echo "$port"
}

# Resolve the configured server port from the service properties (fallback)
# Output: prints the port (empty if not found)
get_port_from_config(){
    local app_props="${EXCHANGIS_CONF_PATH}/application-exchangis.properties"
    local port=""
    if [[ -f "$app_props" ]]; then
        port=$(grep -E "^server[._]port" "$app_props" | head -1 | cut -d'=' -f2 | tr -d '[:space:]')
    fi
    echo "$port"
}

# Check whether the TCP port is still being listened on
# Return 0 if still LISTEN (in use), 1 if released / undetectable
is_port_listening(){
    local port=$1
    if [[ -z "$port" ]]; then return 1; fi
    if command -v lsof >/dev/null 2>&1; then
        if [[ -n "$(lsof -nP -iTCP:${port} -sTCP:LISTEN -t 2>/dev/null)" ]]; then
            return 0
        fi
        return 1
    elif command -v netstat >/dev/null 2>&1; then
        # Matches both Linux ":9321" and macOS "*.9321" local address forms
        if netstat -tln 2>/dev/null | grep -E "[:.]${port}([^0-9]|$)" >/dev/null 2>&1; then
            return 0
        fi
        return 1
    fi
    # No detection tool available: cannot confirm, assume released so we don't block restart
    return 1
}

# Wait until the listening port is released (no longer LISTEN)
# Input: $1 = port, $2 = timeout seconds (default 15)
wait_for_port_release(){
    local port=$1
    if [[ -z "$port" ]]; then return 0; fi
    local now_s=`date '+%s'`
    local stop_s=$((now_s + ${2:-15}))
    while [[ ${now_s} -le ${stop_s} ]]; do
        is_port_listening "$port"
        if [[ $? -ne 0 ]]; then return 0; fi
        sleep ${SLEEP_TIMEREVAL_S}
        now_s=`date '+%s'`
    done
    return 1
}

# Input: $1:module_name, $2:main class, $3:wait timeout seconds (default: 20s for startup)
launcher_start(){
    LOG INFO "Launcher: launch to start server [ $1 ]"
    local wait_timeout=${3:-}
    status_class $1 $2
    if [[ $? -eq 0 ]]; then
      LOG INFO "Launcher: [ $1 ] has been started in process"
      return 0
    fi
    construct_java_command $1 $2
    # Execute
    LOG INFO ${EXEC_JAVA}
    nohup ${EXEC_JAVA}  >/dev/null 2>&1 &
    # Wait timeout: 20s for startup (default). --wait N overrides it (must be a positive integer).
    local startup_wait=20
    if [[ -n "${wait_timeout}" ]] && [[ "${wait_timeout}" =~ ^[0-9]+$ ]] && [[ ${wait_timeout} -gt 0 ]]; then
      startup_wait=${wait_timeout}
    fi
    LOG INFO "Launcher: waiting [ $1 ] to start complete (timeout ${startup_wait}s) ..."
    wait_for_startup ${startup_wait} $1 $2
    if [[ $? -eq 0 ]]; then
        LOG INFO "Launcher: [ $1 ] start success"
        APPLICATION_YML="${EXCHANGIS_CONF_PATH}/application-exchangis.properties"
        EUREKA_URL=`cat ${APPLICATION_YML} | grep Zone | sed -n '1p'`
        LOG INFO "Please check exchangis server in EUREKA_ADDRESS: ${EUREKA_URL#*:} "
    else
        LOG ERROR "Launcher: [ $1 ] start fail over ${startup_wait} seconds, please retry it"
    fi
}

# Input: $1:module_name, $2:main class, $3:force stop (true -> SIGKILL), $4:wait timeout seconds (default: 20s process exit / 15s port release)
launcher_stop(){
    LOG INFO "Launcher: stop the server [ $1 ]"
    local p=""
    local force=${3:-}
    local wait_timeout=${4:-}
    local pid_file_path=${EXCHANGIS_PID_PATH}/$1.pid
    if [ "x"${pid_file_path} != "x" ]; then
      if [ -f ${pid_file_path} ]; then
        local pid_in_file=`cat ${pid_file_path} 2>/dev/null`
        if [ "x"${pid_in_file} !=  "x" ]; then
          p=`${JPS} -q | grep ${pid_in_file} | awk '{print $1}'`
        fi
      fi
    elif [[ "x"$2 != "x" ]]; then
      p=`${JPS} -l | grep "$2" | awk '{print $1}'`
    fi
    if [[ -z ${p} ]]; then
      LOG INFO "Launcher: [ $1 ] didn't start successfully, not found in the java process table"
      return 0
    fi
    # Resolve the listening port BEFORE killing (best effort: live pid first, then config)
    local port=$(get_port_by_pid ${p})
    if [[ -z "${port}" ]]; then
      port=$(get_port_from_config)
    fi
    local signal="SIGTERM"
    if [[ "x${force}" == "xtrue" ]]; then
      signal="SIGKILL"
      LOG INFO "Launcher: [ $1 ] --force enabled, sending SIGKILL to pid [ ${p} ]"
    else
      LOG INFO "Launcher: [ $1 ] sending SIGTERM to pid [ ${p} ], port [ ${port:-unknown} ]"
    fi
    # Wait timeouts: 20s for process exit, 15s for port release (defaults).
    # --wait N overrides both with the same value (must be a positive integer).
    local proc_wait=20
    local port_wait=15
    if [[ -n "${wait_timeout}" ]] && [[ "${wait_timeout}" =~ ^[0-9]+$ ]] && [[ ${wait_timeout} -gt 0 ]]; then
      proc_wait=${wait_timeout}
      port_wait=${wait_timeout}
    fi
    case "`uname`" in
      CYCGWIN*) taskkill /PID "${p}" ;;
      *) kill -${signal} "${p}" ;;
    esac
    LOG INFO "Launcher: waiting [ $1 ] to stop complete (timeout ${proc_wait}s) ..."
    wait_for_stop ${proc_wait} $1 $2
    if [[ $? -ne 0 ]]; then
      LOG ERROR "Launcher: [ $1 ] stop exceeded over ${proc_wait}s, port may not be released; retry with --force (kill -9)" >&2
      return 1
    fi
    # Confirm the listening port has been released before returning success
    if [[ -n "${port}" ]]; then
      LOG INFO "Launcher: waiting [ $1 ] port [ ${port} ] to be released (timeout ${port_wait}s) ..."
      wait_for_port_release "${port}" ${port_wait}
      if [[ $? -eq 0 ]]; then
        LOG INFO "Launcher: [ $1 ] port [ ${port} ] has been released"
      else
        LOG ERROR "Launcher: [ $1 ] port [ ${port} ] still in use after stop" >&2
        return 1
      fi
    else
      LOG WARN "Launcher: [ $1 ] unable to resolve the listening port, skip port-release check"
    fi
    LOG INFO "Launcher: [ $1 ] stop success"
    return 0
}

load_env_definitions ${ENV_FILE}
