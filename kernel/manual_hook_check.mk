define check_ksu_hook
    ifeq ($$(shell grep -q "$(1)" $(2); echo $$$$?),0)
        $$(info -- $$(REPO_NAME)/manual_hook: $(1) found)
    else
        $$(info -- You lost $(1) hook in your kernel)
        $$(info -- Read: https://resukisu.github.io/guide/manual-integrate.html)
        $$(error You should integrate $$(REPO_NAME) in your kernel. $(3))
    endif
endef


  ifeq ($(CONFIG_KSU_MANUAL_HOOK_AUTO_SETUID_HOOK), y)
    $(info -- $(REPO_NAME)/manual_hook: You are using LSM hooks for setuid hooks.)
    ifeq ($(VERSION),6)
      ifeq ($(shell test $(PATCHLEVEL) -ge 8 && echo y),y)
        $(info -- You can't use LSM hooks for kernel version higher than 6.8)
        $(info -- You should turn off CONFIG_KSU_MANUAL_HOOK_AUTO_SETUID_HOOK and hook setresuid manually)
        $(info -- Read: https://resukisu.github.io/guide/manual-integrate.html)
        $(error You can't use LSM hooks when kernel version >= 6.8)
      endif
    endif
  else
    $(info -- $(REPO_NAME)/manual_hook: You are using a manual setresuid hook for setuid hooks.)

    $(eval $(call check_ksu_hook,ksu_handle_setresuid,$(srctree)/kernel/sys.c))
  endif

  ifeq ($(CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK), y)
    $(info -- $(REPO_NAME)/manual_hook: You are using LSM hooks for init rc hooks.)
    ifeq ($(VERSION),6)
      ifeq ($(shell test $(PATCHLEVEL) -ge 8 && echo y),y)
        $(info -- You can't use LSM hooks for kernel version higher than 6.8)
        $(info -- You should turn off CONFIG_KSU_MANUAL_HOOK_AUTO_INITRC_HOOK and hook sys_read manually.)
        $(info -- Read: https://resukisu.github.io/guide/manual-integrate.html)
        $(error You can't use LSM hooks when kernel version >= 6.8)
      endif
    endif
  else
    $(info -- $(REPO_NAME)/manual_hook: You are using a manual sys_read hook for init rc hooks.)

    $(eval $(call check_ksu_hook,ksu_handle_sys_read,$(srctree)/fs/read_write.c))

  endif

  ifeq ($(shell grep -q "ksu_vfs_read_hook" $(srctree)/fs/read_write.c; echo $$?),0)
    $(info -- ksu_vfs_read_hook is incompatible hook)
    $(info -- Read: https://resukisu.github.io/guide/manual-integrate.html)
    $(error You should integrate $(REPO_NAME) in your kernel correctly.)
  endif

  ifeq ($(shell grep -q "is_ksu_transition" $(srctree)/security/selinux/hooks.c; echo $$?),0)
    $(info -- is_ksu_transition is incompatible hook)
    $(info -- Read: https://resukisu.github.io/guide/manual-integrate.html)
    $(error You should integrate $(REPO_NAME) in your kernel correctly.)
  endif

  ifeq ($(CONFIG_KSU_MANUAL_HOOK_AUTO_INPUT_HOOK), y)
    $(info -- $(REPO_NAME)/manual_hook: You are using input_handler for input hooks.)
  else
    $(eval $(call check_ksu_hook,ksu_handle_input_handle_event,$(srctree)/drivers/input/input.c))
  endif

  $(eval $(call check_ksu_hook,ksu_handle_execveat,$(srctree)/fs/exec.c))
  $(eval $(call check_ksu_hook,ksu_handle_faccessat,$(srctree)/fs/open.c))
  $(eval $(call check_ksu_hook,ksu_handle_stat,$(srctree)/fs/stat.c))
  $(eval $(call check_ksu_hook,ksu_handle_newfstat_ret,$(srctree)/fs/stat.c))
  $(eval $(call check_ksu_hook,ksu_handle_fstat64_ret,$(srctree)/fs/stat.c))
  $(eval $(call check_ksu_hook,ksu_handle_sys_reboot,$(srctree)/kernel/reboot.c))

