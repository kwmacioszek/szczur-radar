#!/usr/bin/env python3
"""Self-hosted GitHub Actions runners in KVM VMs on an Ubuntu workstation (README.md, "Runnery na PC").

Each VM runs one runner (its own Docker daemon, so the smoke tests' fixed ports never collide)
with the same label as the Proxmox runner, so GitHub hands jobs to whichever runner is idle.

    python3 pc_runners.py                 # menu
    python3 pc_runners.py create [--count 4 --cpus 4 --ram 8 --disk 60 --yes]
    python3 pc_runners.py status
    python3 pc_runners.py delete [--all | --names ci-pc-1,ci-pc-2] [--yes]

Runs as any user in the `libvirt` group (qemu:///system, no sudo). Needs qemu-kvm,
libvirt-daemon-system, virtinst and cloud-image-utils; `gh` (logged in with admin rights on the
repo) is optional: without it `create` asks for a registration token and `status`/`delete` skip
GitHub. Standard library only.
"""

import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

REPO = "kwmacioszek/szczur-radar"
LABEL = "szczur-ci"
PREFIX = "ci-pc"
POOL = "ci-runners"
POOL_DIR = "/var/lib/libvirt/images/ci-runners"
NETWORK = "default"
IMAGE_URL = "https://cloud-images.ubuntu.com/noble/current/noble-server-cloudimg-amd64.img"
SUMS_URL = "https://cloud-images.ubuntu.com/noble/current/SHA256SUMS"
BASE_VOLUME = "noble-base.qcow2"
VM_USER = "ops"
SETUP_SH = Path(__file__).resolve().parent / "setup.sh"
CACHE_DIR = Path.home() / ".cache" / "szczur-ci-runner"

os.environ.setdefault("LIBVIRT_DEFAULT_URI", "qemu:///system")
os.environ["LC_ALL"] = "C"  # virsh output is parsed below


class Fail(Exception):
    pass


def run(*cmd, check=True, input=None):
    result = subprocess.run(cmd, input=input, capture_output=True, text=True)
    if check and result.returncode != 0:
        raise Fail(f"{' '.join(cmd)}: {result.stderr.strip() or result.stdout.strip()}")
    return result


def virsh(*args, check=True):
    return run("virsh", "-q", *args, check=check)


def ask(prompt, default):
    answer = input(f"{prompt} [{default}]: ").strip()
    return answer or str(default)


def ask_int(prompt, default, minimum=1):
    while True:
        value = ask(prompt, default)
        if value.isdigit() and int(value) >= minimum:
            return int(value)
        print(f"  enter a whole number >= {minimum}")


def confirm(prompt, assume_yes):
    if assume_yes:
        return True
    return input(f"{prompt} [y/N]: ").strip().lower() in ("y", "yes", "t", "tak")


def check_tools():
    missing = [tool for tool in ("virsh", "virt-install", "cloud-localds") if not shutil.which(tool)]
    if missing:
        raise Fail("missing: " + ", ".join(missing)
                + " — sudo apt install qemu-kvm libvirt-daemon-system virtinst cloud-image-utils")
    if virsh("uri", check=False).returncode != 0:
        raise Fail("no access to qemu:///system — add this user to the libvirt group and log in again:"
                " sudo usermod -aG libvirt,kvm $USER")


def gh_available():
    return shutil.which("gh") is not None and run("gh", "auth", "status", check=False).returncode == 0


def gh_runners():
    """The repository's runners by name, or None without a logged-in gh."""
    if not gh_available():
        return None
    out = run("gh", "api", "--paginate", f"repos/{REPO}/actions/runners", "-q", ".runners[]").stdout
    runners = [json.loads(line) for line in out.splitlines() if line.strip()]
    return {runner["name"]: runner for runner in runners}


def vm_names():
    """Our VMs (`<prefix>-<n>`), numerically sorted."""
    names = virsh("list", "--all", "--name").stdout.split()
    ours = [name for name in names if re.fullmatch(rf"{PREFIX}-\d+", name)]
    return sorted(ours, key=lambda name: int(name.rsplit("-", 1)[1]))


def runner_name(vm):
    return f"{vm}-1"  # setup.sh names runners <hostname>-<n>, one runner per VM


def ensure_pool():
    if virsh("pool-info", POOL, check=False).returncode == 0:
        if "running" not in virsh("pool-info", POOL).stdout:
            virsh("pool-start", POOL)
        return
    print(f"creating storage pool {POOL} ({POOL_DIR})")
    virsh("pool-define-as", POOL, "dir", "--target", POOL_DIR)
    virsh("pool-build", POOL)
    virsh("pool-start", POOL)
    virsh("pool-autostart", POOL)


def upload(volume, path):
    """Copies a local file byte for byte into the pool (libvirtd writes it, so no sudo is needed)."""
    virsh("vol-create-as", POOL, volume, f"{path.stat().st_size}b", "--format", "raw")
    virsh("vol-upload", "--pool", POOL, volume, str(path))


def ensure_base_image():
    if virsh("vol-info", "--pool", POOL, BASE_VOLUME, check=False).returncode == 0:
        return
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    image = CACHE_DIR / IMAGE_URL.rsplit("/", 1)[1]
    print(f"downloading {IMAGE_URL}")
    with urllib.request.urlopen(SUMS_URL) as response:
        sums = response.read().decode()
    expected = next((line.split()[0] for line in sums.splitlines() if line.endswith(image.name)), None)
    if expected is None:
        raise Fail(f"{image.name} not in {SUMS_URL}")
    urllib.request.urlretrieve(IMAGE_URL, image)
    digest = hashlib.sha256(image.read_bytes()).hexdigest()
    if digest != expected:
        image.unlink()
        raise Fail(f"SHA-256 mismatch for {image.name}")
    print(f"uploading the base image to pool {POOL}")
    upload(BASE_VOLUME, image)
    virsh("pool-refresh", POOL)
    image.unlink()


def registration_token(given):
    token = given or os.environ.get("RUNNER_TOKEN")
    if token:
        return token
    if gh_available():
        return run("gh", "api", "-X", "POST", f"repos/{REPO}/actions/runners/registration-token",
                "-q", ".token").stdout.strip()
    print("No gh login: get a registration token (valid 1 h) on any machine with admin rights:")
    print(f"  gh api -X POST repos/{REPO}/actions/runners/registration-token -q .token")
    token = input("registration token: ").strip()
    if not token:
        raise Fail("a registration token is required")
    return token


def ssh_keys(path):
    keys = [line.strip() for line in Path(path).expanduser().read_text().splitlines()
            if line.strip().startswith(("ssh-", "ecdsa-"))]
    if not keys:
        raise Fail(f"no public SSH keys in {path}")
    return keys


def user_data(vm, token, keys):
    """cloud-init: user `ops` with the operator's keys, then setup.sh registers one runner."""
    setup = base64.b64encode(SETUP_SH.read_bytes()).decode()
    key_lines = "\n".join(f"      - {key}" for key in keys)
    return f"""#cloud-config
hostname: {vm}
manage_etc_hosts: true
users:
  - name: {VM_USER}
    groups: [sudo]
    sudo: ALL=(ALL) NOPASSWD:ALL
    shell: /bin/bash
    lock_passwd: true
    ssh_authorized_keys:
{key_lines}
write_files:
  - path: /root/ci-runner/setup.sh
    permissions: "0755"
    encoding: b64
    content: {setup}
  - path: /root/ci-runner/token
    permissions: "0600"
    content: {token}
runcmd:
  - [bash, -c, "RUNNER_TOKEN=$(cat /root/ci-runner/token) RUNNER_COUNT=1 RUNNER_LABELS={LABEL} REPO_URL=https://github.com/{REPO} bash /root/ci-runner/setup.sh >/var/log/ci-runner-setup.log 2>&1; rm -f /root/ci-runner/token"]
"""


def create_vm(vm, cpus, ram_gb, disk_gb, token, keys, autostart):
    disk = f"{vm}.qcow2"
    seed = f"{vm}-seed.iso"
    virsh("vol-create-as", POOL, disk, f"{disk_gb}G", "--format", "qcow2",
            "--backing-vol", BASE_VOLUME, "--backing-vol-format", "qcow2")
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        (tmp / "user-data").write_text(user_data(vm, token, keys))
        (tmp / "meta-data").write_text(f"instance-id: {vm}-{int(time.time())}\nlocal-hostname: {vm}\n")
        run("cloud-localds", str(tmp / "seed.iso"), str(tmp / "user-data"), str(tmp / "meta-data"))
        upload(seed, tmp / "seed.iso")
    run("virt-install", "--name", vm, "--vcpus", str(cpus), "--memory", str(ram_gb * 1024),
            "--cpu", "host-passthrough", "--osinfo", "detect=on,require=off", "--import",
            "--disk", f"vol={POOL}/{disk},bus=virtio", "--disk", f"vol={POOL}/{seed},device=cdrom",
            "--network", f"network={NETWORK},model=virtio", "--graphics", "none",
            "--console", "pty,target_type=serial", "--noautoconsole")
    if autostart:
        virsh("autostart", vm)


def cmd_create(args):
    interactive = not args.yes
    count = args.count or (ask_int("How many VMs", 4) if interactive else 4)
    cpus = args.cpus or (ask_int("vCPUs per VM", 4) if interactive else 4)
    ram = args.ram or (ask_int("RAM per VM (GB)", 8) if interactive else 8)
    disk = args.disk or (ask_int("Disk per VM (GB)", 60, minimum=20) if interactive else 60)
    keys_path = args.ssh_keys or (ask("Public SSH keys for the VMs", "~/.ssh/authorized_keys")
            if interactive else "~/.ssh/authorized_keys")
    autostart = not args.no_autostart

    existing = vm_names()
    start = max((int(name.rsplit("-", 1)[1]) for name in existing), default=0) + 1
    new = [f"{PREFIX}-{n}" for n in range(start, start + count)]
    host_cpus = os.cpu_count() or 0
    free_gb = int(re.search(r"MemAvailable:\s+(\d+)", Path("/proc/meminfo").read_text()).group(1)) // 1024 ** 2
    print(f"\nNew VMs: {', '.join(new)} — each {cpus} vCPU, {ram} GB RAM, {disk} GB disk (grows on use),"
            f" label {LABEL}, autostart {'on' if autostart else 'off'}")
    print(f"Host: {host_cpus} threads, {free_gb} GB RAM available; existing VMs: {', '.join(existing) or 'none'}")
    if count * ram > free_gb:
        print(f"WARNING: {count * ram} GB RAM requested, {free_gb} GB available")
    if not confirm("Create them?", args.yes):
        print("nothing created")
        return
    keys = ssh_keys(keys_path)
    token = registration_token(args.token)
    ensure_pool()
    ensure_base_image()
    for vm in new:
        print(f"creating {vm}")
        create_vm(vm, cpus, ram, disk, token, keys, autostart)
    print(f"\nCreated {len(new)} VM(s). The runners register within a few minutes of boot"
            f" (log in the VM: /var/log/ci-runner-setup.log); check with: python3 {Path(__file__).name} status")


def vm_info(vm):
    state = virsh("domstate", vm).stdout.strip()
    info = dict(line.split(":", 1) for line in virsh("dominfo", vm).stdout.splitlines() if ":" in line)
    cpus = info.get("CPU(s)", "?").strip()
    ram = int(info.get("Max memory", "0 KiB").split()[0]) // 1024 ** 2
    ip = "-"
    if state == "running":
        out = virsh("domifaddr", vm, "--source", "lease", check=False).stdout
        match = re.search(r"ipv4\s+([\d.]+)/", out)
        ip = match.group(1) if match else "-"
    autostart = "yes" if info.get("Autostart", "").strip() == "enable" else "no"
    return state, cpus, f"{ram} GB", ip, autostart


def cmd_status(args):
    vms = vm_names()
    runners = gh_runners()
    rows = [("VM", "STATE", "vCPU", "RAM", "IP", "AUTOSTART", "RUNNER", "GITHUB")]
    for vm in vms:
        github = "?" if runners is None else "not registered"
        runner = runners.get(runner_name(vm)) if runners else None
        if runner:
            github = runner["status"] + (", busy" if runner["busy"] else "")
        rows.append((vm, *vm_info(vm), runner_name(vm), github))
    if not vms:
        print("no VMs")
    else:
        widths = [max(len(str(row[i])) for row in rows) for i in range(len(rows[0]))]
        for row in rows:
            print("  ".join(str(cell).ljust(width) for cell, width in zip(row, widths)))
    if runners is None:
        print("\n(gh is not logged in: GitHub runner status skipped)")
        return
    others = [r for name, r in runners.items()
            if LABEL in [label["name"] for label in r["labels"]] and not name.startswith(f"{PREFIX}-")]
    if others:
        print("\nOther runners with label " + LABEL + ": " + ", ".join(
                f"{r['name']} ({r['status']}{', busy' if r['busy'] else ''})" for r in others))


def cmd_delete(args):
    vms = vm_names()
    if not vms:
        print("no VMs")
        return
    if args.all:
        chosen = vms
    elif args.names:
        chosen = [name.strip() for name in args.names.split(",") if name.strip()]
    else:
        print("VMs: " + ", ".join(vms))
        answer = ask("Which to delete (comma-separated names, or 'all')", "all")
        chosen = vms if answer == "all" else [name.strip() for name in answer.split(",") if name.strip()]
    unknown = [name for name in chosen if name not in vms]
    if unknown:
        raise Fail("unknown VM(s): " + ", ".join(unknown))
    runners = gh_runners()
    busy = [vm for vm in chosen if runners and runners.get(runner_name(vm), {}).get("busy")]
    if busy:
        print("WARNING: busy runners (their running job fails and needs `gh run rerun`): " + ", ".join(busy))
    if not confirm(f"Delete {', '.join(chosen)} with their disks?", args.yes):
        print("nothing deleted")
        return
    for vm in chosen:
        runner = runners.get(runner_name(vm)) if runners else None
        if runner:
            run("gh", "api", "-X", "DELETE", f"repos/{REPO}/actions/runners/{runner['id']}")
            print(f"{runner_name(vm)}: removed from GitHub")
        elif runners is None:
            print(f"{runner_name(vm)}: gh not logged in — remove it in GitHub (Settings → Actions → Runners)")
        if virsh("domstate", vm).stdout.strip() == "running":
            virsh("destroy", vm)
        virsh("undefine", vm, "--remove-all-storage")
        for volume in (f"{vm}.qcow2", f"{vm}-seed.iso"):
            virsh("vol-delete", "--pool", POOL, volume, check=False)
        print(f"{vm}: deleted")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command")
    create = commands.add_parser("create", help="create VMs with one runner each")
    create.add_argument("--count", type=int)
    create.add_argument("--cpus", type=int)
    create.add_argument("--ram", type=int, help="GB")
    create.add_argument("--disk", type=int, help="GB")
    create.add_argument("--ssh-keys", help="file with public keys (default ~/.ssh/authorized_keys)")
    create.add_argument("--token", help="registration token (else RUNNER_TOKEN, gh or a prompt)")
    create.add_argument("--no-autostart", action="store_true", help="do not start the VMs with the host")
    create.add_argument("--yes", action="store_true", help="no questions: defaults for missing values")
    commands.add_parser("status", help="VMs and their GitHub runners")
    delete = commands.add_parser("delete", help="remove VMs, their disks and runners")
    delete.add_argument("--all", action="store_true")
    delete.add_argument("--names", help="comma-separated VM names")
    delete.add_argument("--yes", action="store_true")
    args = parser.parse_args()
    if args.command is None:
        choice = ask("1) create  2) delete  3) status", "3")
        command = {"1": "create", "2": "delete", "3": "status"}.get(choice)
        if command is None:
            parser.error(f"unknown choice {choice}")
        args = parser.parse_args([command])
    try:
        check_tools()
        {"create": cmd_create, "status": cmd_status, "delete": cmd_delete}[args.command](args)
    except Fail as error:
        print(f"error: {error}", file=sys.stderr)
        sys.exit(1)
    except KeyboardInterrupt:
        sys.exit(130)


if __name__ == "__main__":
    main()
