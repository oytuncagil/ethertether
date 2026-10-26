# SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
# SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later
#
# The engine reaches framework classes (android.net.*) by reflection. Those classes live in the
# system image, not in the APK, so R8 never renames them.
#
# Shizuku starts the engine's user service by class name (Shizuku.UserServiceArgs), with its
# Context constructor on servers from v13 and the no-argument one before, so that class, both
# constructors, its caller check and its AIDL stub must keep their names.
-keep class io.github.oytuncagil.ethertether.engine.service.EngineUserService {
    public <init>();
    public <init>(android.content.Context);
    public boolean onTransact(int, android.os.Parcel, android.os.Parcel, int);
}
-keep class io.github.oytuncagil.ethertether.engine.service.IEtherTetherService { *; }
-keep class io.github.oytuncagil.ethertether.engine.service.IEtherTetherService$Stub { *; }
