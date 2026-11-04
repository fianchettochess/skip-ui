// Copyright 2025–2026 Skip
// SPDX-License-Identifier: MPL-2.0
#if !SKIP_BRIDGE
import Foundation
import SkipModel
#if SKIP
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
#endif

/// Support for bridged`SwiftUI.@AppStorage`.
///
/// The Compose side manages object lifecycles, so we hold references to the value as a `MutableState` recomposition trigger.
/// We also monitor the underlying defautls key using a preferences listener.
// SKIP @bridge
public final class AppStorageSupport: StateTracker {
    private let key: String
    private let store: UserDefaults?
    private var value: Any?
    private let defaultValue: Any
    private let get: (UserDefaults, Any?) -> Any?
    private let set: (UserDefaults, Any?) -> Void
    #if SKIP
    private let trackingLock = NSRecursiveLock()
    private var state: MutableState<Any?>? = nil
    #endif
    /// The property change listener from the UserDefaults
    private var listener: Any? = nil

    // SKIP @bridge
    public init(_ value: Bool, key: String, store: Any? /* UserDefaults? */) {
        self.key = key
        self.store = store as? UserDefaults
        self.value = value
        self.defaultValue = value
        self.get = { $1 as? Bool ?? $0.bool(forKey: key) }
        self.set = { $0.set($1 as? Bool, forKey: key) }
        StateTracking.register(self)
    }

    // SKIP @bridge
    public init(_ value: Double, key: String, store: Any? /* UserDefaults? */) {
        self.key = key
        self.store = store as? UserDefaults
        self.value = value
        self.defaultValue = value
        self.get = { $1 as? Double ?? $0.double(forKey: key) }
        self.set = { $0.set($1 as? Double, forKey: key) }
        StateTracking.register(self)
    }

    // SKIP @bridge
    public init(_ value: Int, key: String, store: Any? /* UserDefaults? */) {
        self.key = key
        self.store = store as? UserDefaults
        self.value = value
        self.defaultValue = value
        self.get = { $1 as? Int ?? $0.integer(forKey: key) }
        self.set = { $0.set($1 as? Int, forKey: key) }
        StateTracking.register(self)
    }

    // SKIP @bridge
    public init(_ value: String, key: String, store: Any? /* UserDefaults? */) {
        self.key = key
        self.store = store as? UserDefaults
        self.value = value
        self.defaultValue = value
        self.get = { $1 as? String ?? $0.string(forKey: key) }
        self.set = { $0.set($1 as? String, forKey: key) }
        StateTracking.register(self)
    }

    // SKIP @bridge
    public init(_ value: URL, key: String, store: Any? /* UserDefaults? */) {
        self.key = key
        self.store = store as? UserDefaults
        self.value = value
        self.defaultValue = value
        self.get = { $1 as? URL ?? $0.url(forKey: key) }
        self.set = { $0.set($1 as? URL, forKey: key) }
        StateTracking.register(self)
    }

    // SKIP @bridge
    public init(_ value: Data, key: String, store: Any? /* UserDefaults? */) {
        self.key = key
        self.store = store as? UserDefaults
        self.value = value
        self.defaultValue = value
        self.get = { $1 as? Data ?? $0.data(forKey: key) }
        self.set = { $0.set($1 as? Data, forKey: key) }
        StateTracking.register(self)
    }

    // SKIP @bridge
    public var boolValue: Bool? {
        get {
            #if SKIP
            if let state {
                return state.value as? Bool
            }
            #endif
            return value as? Bool
        }
        set {
            #if SKIP
            set(currentStore, newValue)
            #endif
            if listener == nil {
                value = newValue
            }
        }
    }

    // SKIP @bridge
    public var doubleValue: Double? {
        get {
            #if SKIP
            if let state {
                return state.value as? Double
            }
            #endif
            return value as? Double
        }
        set {
            #if SKIP
            set(currentStore, newValue)
            #endif
            if listener == nil {
                value = newValue
            }
        }
    }

    // SKIP @bridge
    public var intValue: Int? {
        get {
            #if SKIP
            if let state {
                return state.value as? Int
            }
            #endif
            return value as? Int
        }
        set {
            #if SKIP
            set(currentStore, newValue)
            #endif
            if listener == nil {
                value = newValue
            }
        }
    }

    // SKIP @bridge
    public var stringValue: String? {
        get {
            #if SKIP
            if let state {
                return state.value as? String
            }
            #endif
            return value as? String
        }
        set {
            #if SKIP
            set(currentStore, newValue)
            #endif
            if listener == nil {
                value = newValue
            }
        }
    }

    // SKIP @bridge
    public var urlValue: URL? {
        get {
            #if SKIP
            if let state {
                return state.value as? URL
            }
            #endif
            return value as? URL
        }
        set {
            #if SKIP
            set(currentStore, newValue)
            #endif
            if listener == nil {
                value = newValue
            }
        }
    }

    // SKIP @bridge
    public var dataValue: Data? {
        get {
            #if SKIP
            if let state {
                return state.value as? Data
            }
            #endif
            return value as? Data
        }
        set {
            #if SKIP
            set(currentStore, newValue)
            #endif
            if listener == nil {
                value = newValue
            }
        }
    }

    /// The current active store
    private var currentStore: UserDefaults {
        #if SKIP
        // TODO: handle Scene.defaultAppStorage() and View.defaultAppStorage() by storing it in the environment
        return store ?? UserDefaults.standard
        #else
        fatalError("unsupported outside of skip")
        #endif
    }

    // SKIP @bridge
    public func trackState() {
        #if SKIP
        trackingLock.lock()
        defer { trackingLock.unlock() }
        let store = self.currentStore
        state = mutableStateOf(value)

        // SharedPreferences holds listeners weakly. Register before reading so a
        // later write to a previously absent key is observed without seeding it.
        self.listener = store.registerOnSharedPreferenceChangeListener(key: key) {
            trackingLock.lock()
            defer { trackingLock.unlock() }
            let object = store.object(forKey: key)
            value = object == nil ? defaultValue : get(store, object)
            state?.value = value
        }
        let object = store.object(forKey: key)
        value = object == nil ? defaultValue : get(store, object)
        state?.value = value
        #endif
    }
}

#endif
