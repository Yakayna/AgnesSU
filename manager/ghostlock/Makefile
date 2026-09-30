API ?= 35

# Auto-detect NDK
ifeq ($(OS),Windows_NT)
  NDK_ROOT ?= $(subst \,/,$(firstword $(wildcard     $(subst \,/,$(LOCALAPPDATA))/Android/Sdk/ndk/*     $(subst \,/,$(ANDROID_HOME))/ndk/*     D:/AndroidSDK/ndk/*)))
  override NDK_ROOT := $(subst \,/,$(NDK_ROOT))
  PREBUILT := windows-x86_64
  CLANGXX_BASE := aarch64-linux-android$(API)-clang++
  NDK_CXX := $(NDK_ROOT)/toolchains/llvm/prebuilt/$(PREBUILT)/bin/$(CLANGXX_BASE).cmd
else
  NDK_ROOT ?= $(or $(ANDROID_NDK_HOME),$(ANDROID_NDK_ROOT))
  PREBUILT := linux-x86_64
  NDK_CXX := $(NDK_ROOT)/toolchains/llvm/prebuilt/$(PREBUILT)/bin/aarch64-linux-android$(API)-clang++
endif

# Production translation units (YuKongA/ghostlock-app src/Makefile CXX_SRCS).
CXX_SRCS := \
  src/core/main.cpp \
  src/core/attack/ops.cpp \
  src/core/profile/entry.cpp \
  src/core/memory/address_space.cpp \
  src/core/memory/heap_context.cpp \
  src/core/race/pi_race.cpp \
  src/core/route/route_controller.cpp \
  src/core/route/route_middleware.cpp \
  src/core/race/threads.cpp \
  src/core/route/tcp_zerocopy_route.cpp \
  src/core/route/select_stack_route.cpp \
  src/core/route/multicast_waiter_route.cpp \
  src/core/memory/payload_builder.cpp \
  src/core/session/runtime_config.cpp \
  src/core/profile/binary.cpp \
  src/core/support/util.cpp \
  src/core/session/exploit_session.cpp \
  src/core/session/root_child_frontend.cpp \
  src/core/session/backend/cve_2026_43499_backend.cpp \
  src/core/session/handoff_probe.cpp \
  src/core/session/victim_process.cpp \
  src/core/session/victim_context.cpp \
  src/core/support/native_resource.cpp \
  src/core/support/run_state.cpp

# Headers also trigger a rebuild.
HDRS := $(wildcard src/core/*.h src/core/*.hpp src/core/*/*.h src/core/*/*.hpp)

# Device offsets are resolved at runtime; kernel/target.h is the only compile-time config.
TARGET_CONFIG ?= kernel/target.h

CXXFLAGS = -O2 -flto -Wall -Wextra -Wconversion -Wsign-conversion \
  -Wno-unused-parameter -Wno-sign-compare -Wno-unused-function \
  -Isrc/core -DTARGET_CONFIG_H=\"$(TARGET_CONFIG)\" -std=c++23 -fno-rtti
LDFLAGS := -fPIE -pie -pthread -flto -static-libstdc++

.PHONY: all clean product

all: ghostlock

ghostlock: $(CXX_SRCS) $(HDRS)
	@echo "Using NDK C++ compiler: $(NDK_CXX)"
	@echo "Target config: $(TARGET_CONFIG)"
	$(NDK_CXX) $(CXXFLAGS) $(LDFLAGS) $(CXX_SRCS) -o ghostlock

product: ghostlock
	@echo "=== ghostlock binary ready: ./ghostlock ==="
	@echo "构建 APK: .\gradlew.bat :app:assembleDebug"

clean:
	rm -f ghostlock
