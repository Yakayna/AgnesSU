use crate::android::susfs::magic::{
    NEW_UTS_LEN, SUSFS_ENABLED_FEATURES_SIZE, SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE,
    SUSFS_MAX_LEN_PATHNAME, SUSFS_MAX_VARIANT_BUFSIZE, SUSFS_MAX_VERSION_BUFSIZE,
};

#[repr(C)]
pub(super) struct SusfsSusPath {
    pub(super) target_ino: u64,
    pub(super) target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    pub(super) i_uid: u32,
    pub(super) err: i32,
}

impl Default for SusfsSusPath {
    fn default() -> Self {
        Self {
            target_ino: 0,
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            i_uid: 0,
            err: 0,
        }
    }
}

#[repr(C)]
pub(super) struct ExternalDir {
    pub(super) target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    pub(super) is_inited: bool,
    pub(super) cmd: i32,
    pub(super) err: i32,
}

impl Default for ExternalDir {
    fn default() -> Self {
        Self {
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            is_inited: false,
            cmd: 0,
            err: 0,
        }
    }
}

#[repr(C)]
#[derive(Default)]
pub(super) struct SusfsHideSusMnts {
    pub(super) enabled: bool,
    pub(super) err: i32,
}

#[repr(C)]
pub(super) struct SusfsSusKstat {
    pub(super) is_statically: bool,
    pub(super) target_ino: u64,
    pub(super) target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    pub(super) spoofed_ino: u64,
    pub(super) spoofed_dev: u64,
    pub(super) spoofed_nlink: u32,
    pub(super) spoofed_size: i64,
    pub(super) spoofed_atime_tv_sec: i64,
    pub(super) spoofed_mtime_tv_sec: i64,
    pub(super) spoofed_ctime_tv_sec: i64,
    pub(super) spoofed_atime_tv_nsec: i64,
    pub(super) spoofed_mtime_tv_nsec: i64,
    pub(super) spoofed_ctime_tv_nsec: i64,
    pub(super) spoofed_blksize: u64,
    pub(super) spoofed_blocks: u64,
    pub(super) err: i32,
}

impl Default for SusfsSusKstat {
    fn default() -> Self {
        Self {
            is_statically: false,
            target_ino: 0,
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            spoofed_ino: 0,
            spoofed_dev: 0,
            spoofed_nlink: 0,
            spoofed_size: 0,
            spoofed_atime_tv_sec: 0,
            spoofed_mtime_tv_sec: 0,
            spoofed_ctime_tv_sec: 0,
            spoofed_atime_tv_nsec: 0,
            spoofed_mtime_tv_nsec: 0,
            spoofed_ctime_tv_nsec: 0,
            spoofed_blksize: 0,
            spoofed_blocks: 0,
            err: 0,
        }
    }
}

#[repr(C)]
pub(super) struct SusfsUname {
    pub(super) release: [u8; NEW_UTS_LEN + 1],
    pub(super) version: [u8; NEW_UTS_LEN + 1],
    pub(super) err: i32,
}

impl Default for SusfsUname {
    fn default() -> Self {
        Self {
            release: [0; NEW_UTS_LEN + 1],
            version: [0; NEW_UTS_LEN + 1],
            err: 0,
        }
    }
}

#[repr(C)]
#[derive(Default)]
pub(super) struct SusfsLog {
    pub(super) enabled: bool,
    pub(super) err: i32,
}

#[repr(C)]
pub(super) struct SusfsSpoofCmdline {
    pub(super) fake_cmdline_or_bootconfig: [u8; SUSFS_FAKE_CMDLINE_OR_BOOTCONFIG_SIZE],
    pub(super) err: i32,
}

#[repr(C)]
pub(super) struct SusfsOpenRedirect {
    pub(super) target_ino: u64,
    pub(super) target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    pub(super) redirected_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    pub(super) err: i32,
}

impl Default for SusfsOpenRedirect {
    fn default() -> Self {
        Self {
            target_ino: 0,
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            redirected_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            err: 0,
        }
    }
}

#[repr(C)]
pub(super) struct SusfsSusMap {
    pub(super) target_pathname: [u8; SUSFS_MAX_LEN_PATHNAME],
    pub(super) err: i32,
}

impl Default for SusfsSusMap {
    fn default() -> Self {
        Self {
            target_pathname: [0; SUSFS_MAX_LEN_PATHNAME],
            err: 0,
        }
    }
}

#[repr(C)]
#[derive(Default)]
pub(super) struct SusfsAvcLogSpoofing {
    pub(super) enabled: bool,
    pub(super) err: i32,
}

#[repr(C)]
pub(super) struct SusfsEnabledFeatures {
    pub(super) enabled_features: [u8; SUSFS_ENABLED_FEATURES_SIZE],
    pub(super) err: i32,
}

#[repr(C)]
pub(super) struct SusfsVariant {
    pub(super) susfs_variant: [u8; SUSFS_MAX_VARIANT_BUFSIZE],
    pub(super) err: i32,
}

#[repr(C)]
pub(super) struct SusfsVersion {
    pub(super) susfs_version: [u8; SUSFS_MAX_VERSION_BUFSIZE],
    pub(super) err: i32,
}
