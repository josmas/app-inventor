#!/usr/bin/python
# -*- coding: utf-8; fill-column: 120 -*-
import os
import platform
import re
import shlex
import subprocess
import sys
import time
import config

from bottle import run, route, response

VERSION = '%d.%d.%d%s' % (config.ANDROID_PLATFORM, config.COMPANION_VERSION, config.MINOR_VERSION, config.BUILD_EXTRAS)

PLATDIR = os.path.abspath(os.path.dirname(sys.argv[0]))

# Path to executables — use bundled adb if present, otherwise fall back to PATH
_bundled_adb = os.path.join(PLATDIR, 'from-Android-SDK', 'platform-tools', 'adb')
ADB = _bundled_adb if os.path.exists(_bundled_adb) else 'adb'
RUN_EMULATOR = os.path.join(PLATDIR, 'run-emulator')
RESET_EMULATOR = os.path.join(PLATDIR, 'reset-emulator')
KILL_EMULATOR = os.path.join(PLATDIR, 'kill-emulator')


@route('/ping/')
def ping():
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Headers'] = 'origin, content-type'
    response.headers['Content-Type'] = 'application/json'
    return {
        "status": "OK",
        "version": VERSION
    }


@route('/utest/')
def utest():
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Headers'] = 'origin, content-type'
    response.headers['Content-Type'] = 'application/json'
    device = checkrunning(False)
    if device:
        return {
            "status": "OK",
            "device": device,
            "version": VERSION
        }
    else:
        return {
            "status": "NO",
            "version": VERSION
        }


@route('/start/')
def start():
    subprocess.call(RUN_EMULATOR, shell=True)
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Headers'] = 'origin, content-type'
    return ''


@route('/emulatorreset/')
def emulatorreset():
    subprocess.call(RESET_EMULATOR, shell=True)
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Headers'] = 'origin, content-type'
    return ''


@route('/echeck/')
def echeck():
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Headers'] = 'origin, content-type'
    response.headers['Content-Type'] = 'application/json'
    device = checkrunning(True)
    if device:
        return {
            "status": "OK",
            "device": device,
            "version": VERSION
        }
    else:
        return {
            "status": "NO",
            "version": VERSION
        }


@route('/ucheck/')
def ucheck():
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Headers'] = 'origin, content-type'
    response.headers['Content-Type'] = 'application/json'
    device = checkrunning(False)
    if device:
        return {
            "status": "OK",
            "device": device,
            "version": VERSION
        }
    else:
        return {
            "status": "NO",
            "version": VERSION
        }


@route('/reset/')
def reset():
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Headers'] = 'origin, content-type'
    response.headers['Content-Type'] = 'application/json'
    shutdown()
    return {
        "status": "OK",
        "version": VERSION
    }


@route('/deviceip/:device')
def deviceip(device=None):
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Headers'] = 'origin, content-type'
    response.headers['Content-Type'] = 'application/json'
    try:
        quoted = shlex.quote(device)
        output = subprocess.check_output('"%s" -s %s shell ip route' % (ADB, quoted), shell=True)
        output = str(output, 'utf-8')
        m = re.search(r'src\s+(\d+\.\d+\.\d+\.\d+)', output)
        if m:
            return {"status": "OK", "ip": m.group(1), "version": VERSION}
        return {"status": "NO", "version": VERSION}
    except subprocess.CalledProcessError:
        return {"status": "NO", "version": VERSION}


@route('/devices/')
def devices():
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Headers'] = 'origin, content-type'
    response.headers['Content-Type'] = 'application/json'
    return {
        "status": "OK",
        "devices": listdevices(),
        "version": VERSION
    }


@route('/replstart/:device')
def replstart(device=None):
    print('Device =', device)
    quoted = shlex.quote(device)
    keyevent = 'input keyevent 82 ; ' if re.match('emulator.*', device) else ''
    shell_cmd = (
        'am force-stop edu.mit.appinventor.aicompanion3 ; '
        + keyevent +
        'am start -a android.intent.action.VIEW '
        '-n edu.mit.appinventor.aicompanion3/.Screen1 --ez rundirect true'
    )
    # Step 1: start companion (force-stop + am start in one shell session)
    # mDNS TLS devices may need a moment to reconnect after daemon restart — retry a few times
    for attempt in range(5):
        try:
            subprocess.check_output('"%s" -s %s shell "%s"' % (ADB, quoted, shell_cmd), shell=True)
            break
        except subprocess.CalledProcessError as e:
            print('Attempt %d failed (status %d), retrying...' % (attempt + 1, e.returncode))
            if attempt < 4:
                time.sleep(2)
    # Step 2: set up port forwarding AFTER companion start while daemon is still alive
    # The companion security model only accepts requests from 127.0.0.1, so forwarding is required
    try:
        subprocess.check_output('"%s" -s %s forward tcp:8001 tcp:8001' % (ADB, quoted), shell=True)
        print('Port forwarding established')
    except subprocess.CalledProcessError as e:
        print('Port forwarding failed:', e.returncode)
    response.headers['Access-Control-Allow-Origin'] = '*'
    response.headers['Access-Control-Allow-Headers'] = 'origin, content-type'
    return ''


def listdevices():
    """Return list of all connected non-emulator ADB device IDs (any format)."""
    try:
        result = subprocess.check_output('"%s" devices' % ADB, shell=True)
        devlist = []
        for line in result.splitlines()[1:]:
            line = str(line, 'utf-8')
            match = re.search(r'^(.+?)\tdevice$', line)
            if match:
                device = match.group(1)
                if not re.match(r'^emulator-\d+$', device):
                    devlist.append(device)
        return devlist
    except subprocess.CalledProcessError as e:
        print('Problem listing devices : status', e.returncode)
        return []


def checkrunning(emulator):
    try:
        result = subprocess.check_output('"%s" devices' % ADB, shell=True)
        for line in result.splitlines()[1:]:
            line = str(line, 'utf-8')
            if emulator:
                match = re.search(r'^(emulator-\d+)\tdevice$', line)
            else:
                if re.match(r'^emulator-\d+\t', line):
                    continue
                match = re.search(r'^(.+?)\tdevice$', line)
            if match:
                return match.group(1)
        return False
    except subprocess.CalledProcessError as e:
        print('Problem checking for devices : status', e.returncode)
        return False


def killadb():
    try:
        subprocess.check_output('"%s" kill-server' % ADB, shell=True)
        print('Killed adb')
    except subprocess.CalledProcessError as e:
        print('Problem stopping adb : status', e.returncode)


def killemulator():
    try:
        subprocess.check_output('"%s"' % KILL_EMULATOR, shell=True)
        print('Killed emulator')
    except subprocess.CalledProcessError as e:
        print('Problem stopping emulator : status', e.returncode)


def shutdown():
    try:
        killemulator()
        killadb()
    except:
        pass


if __name__ == '__main__':
    print('App Inventor version:', VERSION, '\n')
    print('Architecture:', platform.machine(), '\n')
    print('AppInventor tools located here:', PLATDIR, '\n')
    print('ADB path:', ADB)

    import atexit
    atexit.register(shutdown)

    run(host='127.0.0.1', port=8004)
