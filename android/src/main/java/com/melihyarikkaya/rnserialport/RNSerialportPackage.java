package com.melihyarikkaya.rnserialport;

import com.facebook.react.BaseReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.module.model.ReactModuleInfo;
import com.facebook.react.module.model.ReactModuleInfoProvider;

import java.util.HashMap;
import java.util.Map;

public class RNSerialportPackage extends BaseReactPackage {

    @Override
    public NativeModule getModule(String name, ReactApplicationContext reactContext) {
        if (name.equals(RNSerialportModule.NAME)) {
            return new RNSerialportModule(reactContext);
        }
        return null;
    }

    @Override
    public ReactModuleInfoProvider getReactModuleInfoProvider() {
        return () -> {
            Map<String, ReactModuleInfo> map = new HashMap<>();
            map.put(
                RNSerialportModule.NAME,
                new ReactModuleInfo(
                    RNSerialportModule.NAME, // JS module name
                    RNSerialportModule.NAME, // class name
                    false, // canOverrideExistingModule
                    false, // needsEagerInit
                    false, // hasConstants — set to false if you don’t implement getConstants
                    false, // isCxxModule
                    true   // isTurboModule
                )
            );
            return map;
        };
    }
}
