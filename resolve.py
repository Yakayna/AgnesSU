import subprocess
import os

def run(cmd):
    return subprocess.check_output(cmd, shell=True, text=True)

status = run('git status --porcelain').splitlines()
for line in status:
    state = line[:2]
    file = line[3:].strip()
    if state in ['UU', 'UD', 'DU', 'UA', 'AU', 'AA']:
        if state == 'DU':
            # Deleted by us, updated by them -> keep our deletion
            print(f'Removing {file}')
            os.system(f'git rm -q "{file}"')
        elif state == 'UD':
            # Updated by us, deleted by them -> keep our file
            print(f'Keeping our {file}')
            os.system(f'git checkout --ours "{file}"')
            os.system(f'git add "{file}"')
        else:
            # UU, UA, etc. We want to keep OUR changes completely
            print(f'Checking out ours for {file}')
            os.system(f'git checkout --ours "{file}"')
            os.system(f'git add "{file}"')
