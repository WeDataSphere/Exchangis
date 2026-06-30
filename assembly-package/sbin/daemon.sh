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

load_env_definitions ${ENV_FILE}
if [[ "x"${EXCHANGIS_HOME} != "x" ]]; then
  source ${EXCHANGIS_HOME}/sbin/launcher.sh
  source ${EXCHANGIS_HOME}/sbin/common.sh
else
  source ./launcher.sh
  source ./common.sh
fi

usage(){
  echo "Usage: daemon.sh [start|stop|restart] {server} [--force] [--wait <seconds>]"
  echo "  --force              stop/restart with kill -9 (SIGKILL); default is graceful SIGTERM"
  echo "  --wait <seconds>     override wait timeout in seconds, applies to BOTH start and stop"
  echo "                       (default: 20s startup, 20s process exit, 15s port release)"
}

start(){
  if [[ "x${FORCE_STOP:-}" == "xtrue" ]]; then
    LOG WARN "--force has no effect on start, ignored"
  fi
  # call launcher (args: module, main_class, wait_timeout) -- --wait applies to startup too
  launcher_start "$1" "$2" "${WAIT_TIMEOUT:-}"
}

stop(){
  # call launcher (args: module, main_class, force, wait_timeout)
  launcher_stop "$1" "$2" "${FORCE_STOP:-}" "${WAIT_TIMEOUT:-}"
}

restart(){
  launcher_stop "$1" "$2" "${FORCE_STOP:-}" "${WAIT_TIMEOUT:-}"
  if [[ $? -eq 0 ]]; then
    sleep 3
    launcher_start $1 $2
  fi
}

COMMAND=$1
case $COMMAND in
  start|stop|restart)
    if [[ ! -z $2 ]]; then
      # Parse optional flags: --force/-f, --wait/-w/--timeout <seconds>|--wait=<seconds>
      FORCE_STOP=""
      WAIT_TIMEOUT=""
      _rest=("${@:3}")
      _idx=0
      while [[ ${_idx} -lt ${#_rest[@]} ]]; do
        _opt="${_rest[$_idx]}"
        case "$_opt" in
          --force|-f)
            FORCE_STOP="true"; _idx=$(( _idx + 1 )) ;;
          --wait|-w|--timeout)
            _val="${_rest[$(( _idx + 1 ))]}"
            if [[ -z "$_val" || "$_val" == -* ]]; then
              LOG ERROR "--wait requires a numeric value (seconds)"
              usage; exit 1
            fi
            if ! [[ "$_val" =~ ^[0-9]+$ ]] || [[ ${_val} -eq 0 ]]; then
              LOG ERROR "--wait must be a positive integer, got: [ $_val ]"
              usage; exit 1
            fi
            WAIT_TIMEOUT="$_val"; _idx=$(( _idx + 2 )) ;;
          --wait=*|--timeout=*)
            _val="${_opt#*=}"
            if ! [[ "$_val" =~ ^[0-9]+$ ]] || [[ ${_val} -eq 0 ]]; then
              LOG ERROR "--wait must be a positive integer, got: [ $_val ]"
              usage; exit 1
            fi
            WAIT_TIMEOUT="$_val"; _idx=$(( _idx + 1 )) ;;
          *)
            LOG ERROR "Unknown option: [ $_opt ]"
            usage; exit 1 ;;
        esac
      done
      SERVICE_NAME=${MODULE_DEFAULT_PREFIX}$2${MODULE_DEFAULT_SUFFIX}
      MAIN_CLASS=${MODULE_MAIN_CLASS[${SERVICE_NAME}]}
      if [[ "x"${MAIN_CLASS} != "x" ]]; then
        $COMMAND ${SERVICE_NAME} ${MAIN_CLASS}
      else
        LOG ERROR "Cannot find the main class for [ ${SERVICE_NAME} ]"
      fi
    else
      usage
      exit 1
    fi
    ;;
  *)
    usage
    exit 1
    ;;
esac