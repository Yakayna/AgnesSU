  ifeq ($(shell grep -q "ksu_handle_setresuid" $(srctree)/kernel/sys.c; echo $$?),0)
    $(info -- $(REPO_NAME)/susfs_inline: ksu_handle_setresuid found)
  else
    $(info -- ERROR: Missing ksu_handle_setresuid hook in kernel/sys.c)
    $(error Please integrate KernelSU hooks first: https://kernelsu.org/guide/how-to-integrate-for-non-gki.html)
  endif

  ifeq ($(shell grep -q "ksu_handle_execveat" $(srctree)/fs/exec.c; echo $$?),0)
    $(info -- $(REPO_NAME)/susfs_inline: ksu_handle_execveat found)
  else
    $(info -- ERROR: Missing ksu_handle_execveat hook in fs/exec.c)
    $(error Please integrate KernelSU hooks first)
  endif

  ifeq ($(shell grep -q "ksu_handle_faccessat" $(srctree)/fs/open.c; echo $$?),0)
    $(info -- $(REPO_NAME)/susfs_inline: ksu_handle_faccessat found)
  else
    $(info -- ERROR: Missing ksu_handle_faccessat hook in fs/open.c)
    $(error Please integrate KernelSU hooks first)
  endif

  ifeq ($(shell grep -q "ksu_handle_stat" $(srctree)/fs/stat.c; echo $$?),0)
    $(info -- $(REPO_NAME)/susfs_inline: ksu_handle_stat found)
  else
    $(info -- ERROR: Missing ksu_handle_stat hook in fs/stat.c)
    $(error Please integrate KernelSU hooks first)
  endif

  ifeq ($(shell grep -q "ksu_handle_sys_reboot" $(srctree)/kernel/reboot.c; echo $$?),0)
    $(info -- $(REPO_NAME)/susfs_inline: ksu_handle_sys_reboot found)
  else
    $(info -- ERROR: Missing ksu_handle_sys_reboot hook in kernel/reboot.c)
    $(error Please integrate KernelSU hooks first)
  endif

  ifeq ($(shell grep -q "ksu_handle_input_handle_event" $(srctree)/drivers/input/input.c; echo $$?),0)
    $(info -- $(REPO_NAME)/susfs_inline: ksu_handle_input_handle_event found)
  else
    $(info -- ERROR: Missing ksu_handle_input_handle_event hook in drivers/input/input.c)
    $(error Please integrate KernelSU hooks first)
  endif

  ifeq ($(shell grep -q "ksu_handle_devpts" $(srctree)/fs/devpts/inode.c; echo $$?),0)
    $(info -- $(REPO_NAME)/susfs_inline: ksu_handle_devpts found)
  else
    $(info -- ERROR: Missing ksu_handle_devpts hook in fs/devpts/inode.c)
    $(error Please integrate KernelSU hooks first)
  endif
