# SELinux drivers check
ifeq ($(shell grep -q "current_sid(void)" $(srctree)/security/selinux/include/objsec.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: current_sid found)
ccflags-y += -DKSU_COMPAT_HAS_CURRENT_SID
endif
ifeq ($(shell grep -q "struct selinux_state " $(srctree)/security/selinux/include/security.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: selinux_state found)
ccflags-y += -DKSU_COMPAT_HAS_SELINUX_STATE
endif

# Handle optional backports
ifeq ($(shell grep -q "strncpy_from_user_nofault" $(srctree)/include/linux/uaccess.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: strncpy found)
ccflags-y += -DKSU_OPTIONAL_STRNCPY
endif

ifeq ($(shell grep -q "ssize_t kernel_read" $(srctree)/fs/read_write.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat: kernel_read found)
ccflags-y += -DKSU_OPTIONAL_KERNEL_READ
endif

ifeq ($(shell grep "ssize_t kernel_write" $(srctree)/fs/read_write.c | grep -q "const void" ; echo $$?),0)
$(info -- $(REPO_NAME)/compat: kernel_write found)
ccflags-y += -DKSU_OPTIONAL_KERNEL_WRITE
endif

ifeq ($(shell grep -q "int\s\+path_mount" $(srctree)/fs/namespace.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat: path_mount found)
ccflags-y += -DKSU_HAS_PATH_MOUNT
endif

ifeq ($(shell grep -q "int\s\+path_umount" $(srctree)/fs/namespace.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat: path_umount found)
ccflags-y += -DKSU_HAS_PATH_UMOUNT
endif

ifeq ($(shell grep -q "inode_security_struct\s\+\*selinux_inode" $(srctree)/security/selinux/include/objsec.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: selinux_inode found)
ccflags-y += -DKSU_OPTIONAL_SELINUX_INODE
endif

ifeq ($(shell grep -q "task_security_struct\s\+\*selinux_cred" $(srctree)/security/selinux/include/objsec.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: selinux_cred found)
ccflags-y += -DKSU_OPTIONAL_SELINUX_CRED
endif

# seccomp_types.h was added in 6.7
ifeq ($(shell grep -q "atomic_t\s\+filter_count" $(srctree)/include/linux/seccomp.h $(srctree)/include/linux/seccomp_types.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: seccomp_filter_count found)
ccflags-y += -DKSU_OPTIONAL_SECCOMP_FILTER_CNT
endif

# some old kernels backport this, so check whether put_seccomp_filter still exists
ifneq ($(shell grep -wq "put_seccomp_filter" $(srctree)/kernel/seccomp.c $(srctree)/include/linux/seccomp.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: put_seccomp_filter found)
ccflags-y += -DKSU_OPTIONAL_SECCOMP_FILTER_RELEASE
endif

ifeq ($(shell grep -q "security_inode_init_security_anon" $(srctree)/include/linux/security.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: security_inode_init_security_anon found)
ccflags-y += -DKSU_OPTIONAL_HAS_INIT_SEC_ANON
endif

ifeq ($(shell grep -q "anon_inode_getfd_secure" $(srctree)/fs/anon_inodes.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat: anon_inode_getfd_secure found)
ccflags-y += -DKSU_HAS_GETFD_SECURE
endif

ifeq ($(shell grep -A1 "^int vfs_getattr" $(srctree)/fs/stat.c | grep -q "query_flags"; echo $$?),0)
$(info -- $(REPO_NAME)/compat: vfs_getattr() found)
ccflags-y += -DKSU_HAS_NEW_VFS_GETATTR
endif

ifeq ($(shell grep -q "static inline struct inode \*file_inode" $(srctree)/include/linux/fs.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: file_inode() found)
ccflags-y += -DKSU_UL_HAS_FILE_INODE
endif

ifneq ($(shell grep -q __flush_dcache_area $(srctree)/arch/arm64/include/asm/cacheflush.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: new dcahce flush found)
ccflags-y += -DKSU_HAS_NEW_DCACHE_FLUSH
endif

# Checks Samsung
ifeq ($(shell grep -q "CONFIG_KDP_CRED" $(srctree)/kernel/cred.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat/samsung: CONFIG_KDP_CRED found)
ccflags-y += -DSAMSUNG_UH_DRIVER_EXIST
endif

ifeq ($(shell grep -q "SEC_SELINUX_PORTING_COMMON" $(srctree)/security/selinux/avc.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat/samsung: SEC_SELINUX_PORTING_COMMON found)
ccflags-y += -DSAMSUNG_SELINUX_PORTING
endif

## For Huawei EMUI10+ check  
# Scan Kernel Tree to find CONFIG_HKIP_SELINUX_PROT in ebitmap.h
ifeq ($(shell grep -q "CONFIG_HKIP_SELINUX_PROT" $(srctree)/security/selinux/ss/ebitmap.h 2>/dev/null; echo $$?),0)
  $(info -- $(REPO_NAME): CONFIG_HKIP_SELINUX_PROT found!)
  ccflags-y += -DKSU_COMPAT_IS_HISI_HM2
endif

# Function proc_ops check
ifeq ($(shell grep -q "struct proc_ops " $(srctree)/include/linux/proc_fs.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: proc_ops found)
ccflags-y += -DKSU_COMPAT_HAS_PROC_OPS
endif

# Bitmap zalloc
# https://github.com/torvalds/linux/commit/c42b65e363ce97a828f81b59033c3558f8fa7f70
ifeq ($(shell grep -q "bitmap_zalloc" $(srctree)/include/linux/bitmap.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: bitmap_zalloc found)
ccflags-y += -DKSU_COMPAT_HAS_BITMAP_ALLOC_HELPER
endif

# policy mutex
# kernel 5.10+
ifeq ($(shell grep -q "policy_mutex" $(srctree)/security/selinux/include/security.h; echo $$?),0)
$(info -- $(REPO_NAME)/compat: policy_mutex found)
ccflags-y += -DKSU_COMPAT_HAS_POLICY_MUTEX
endif

# policy rwlock
# kernel 4.14-
ifeq ($(shell grep -q "^DEFINE_RWLOCK(policy_rwlock);" $(srctree)/security/selinux/ss/services.c; echo $$?),0)
$(info -- $(REPO_NAME)/compat: exported policy_rwlock found!)
ccflags-y += -DKSU_COMPAT_HAS_EXPORTED_POLICY_RWLOCK
endif