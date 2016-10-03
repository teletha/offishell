/*
 * Copyright (C) 2016 Nameless Production Committee
 *
 * Licensed under the MIT License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://opensource.org/licenses/mit-license.php
 */
package offishell.macro;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WORD;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.HHOOK;
import com.sun.jna.platform.win32.WinUser.INPUT;
import com.sun.jna.platform.win32.WinUser.KBDLLHOOKSTRUCT;
import com.sun.jna.platform.win32.WinUser.KEYBDINPUT;
import com.sun.jna.platform.win32.WinUser.LowLevelKeyboardProc;
import com.sun.jna.platform.win32.WinUser.MSG;

import kiss.I;

/**
 * @version 2016/10/04 3:22:04
 */
public abstract class Macro {

    /** Acceptable condition. */
    private static final Predicate ANY = new Predicate() {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean test(Object o) {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Predicate and(Predicate other) {
            return other;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Predicate or(Predicate other) {
            return other;
        }
    };

    /** The window condition. */
    private Predicate<Window> windowCondition = ANY;

    /** The keyboard hook. */
    private NativeKeyboardHook keyboard = new NativeKeyboardHook();

    /**
     * 
     */
    protected Macro() {
        keyboard.install();
    }

    /**
     * <p>
     * Declare press event hook.
     * </p>
     * 
     * @param key
     * @return
     */
    protected final MacroDSL whenPress(Key key) {
        return new KeyMacro().when(keyboard.presses).key(key);
    }

    /**
     * <p>
     * Emulate press event.
     * </p>
     * 
     * @param key
     * @return
     */
    protected final Macro press(Key key) {
        INPUT ip = new INPUT();
        ip.type = new DWORD(INPUT.INPUT_KEYBOARD);
        ip.input.setType("ki");
        ip.input.ki.wVk = new WORD(key.virtualCode);
        ip.input.ki.wScan = new WORD(key.scanCode);

        // press
        ip.input.ki.dwFlags = new DWORD(KEYBDINPUT.KEYEVENTF_SCANCODE);
        User32.INSTANCE.SendInput(new DWORD(1), new INPUT[] {ip}, ip.size());

        // release
        ip.input.ki.dwFlags = new DWORD(KEYBDINPUT.KEYEVENTF_KEYUP | KEYBDINPUT.KEYEVENTF_SCANCODE);
        User32.INSTANCE.SendInput(new DWORD(1), new INPUT[] {ip}, ip.size());
        return this;
    }

    protected final Macro delay(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw I.quiet(e);
        }
        return this;
    }

    /**
     * <p>
     * Declare the condition of macro activation.
     * </p>
     * 
     * @param condition
     */
    protected final void require(Predicate<Window> condition) {
        windowCondition = windowCondition.and(condition);
    }

    /**
     * <p>
     * Declare the condition of macro activation.
     * </p>
     * 
     * @param condition
     */
    protected final void requireTitle(String title) {
        require(window -> window.title().contains(title));
    }

    /**
     * <p>
     * Use the specified macro.
     * </p>
     */
    public static <M extends Macro> void use(Class<M> clazz) {
        Macro macro = I.make(clazz);
    }

    /**
     * @version 2016/10/02 18:01:25
     */
    public interface MacroDSL {

        /**
         * <p>
         * Declare actual action.
         * </p>
         * 
         * @param action
         */
        void run(Runnable action);

        /**
         * <p>
         * Consume the native event.
         * </p>
         * 
         * @return
         */
        MacroDSL consume();
    }

    /**
     * @version 2016/10/02 17:30:48
     */
    private class KeyMacro implements MacroDSL {

        /** The window condition. */
        private Predicate<Window> window = windowCondition;

        /** The acceptable event type. */
        private Predicate<Key> condition = ANY;

        /** The event should be consumed or not. */
        private boolean consumable;

        /** The actual action. */
        private Runnable action = () -> {
        };

        /**
         * <p>
         * Set key type.
         * </p>
         * 
         * @param key
         * @return
         */
        private KeyMacro key(Key key) {
            condition = condition.and(e -> e == key);

            return this;
        }

        /**
         * @param presses
         * @return
         */
        public KeyMacro when(List<KeyMacro> type) {
            type.add(this);

            return this;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run(Runnable action) {
            if (action != null) {
                this.action = action;
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public MacroDSL consume() {
            consumable = true;
            return this;
        }
    }

    /**
     * @version 2016/10/03 12:31:30
     */
    private static class NativeKeyboardHook implements Runnable, LowLevelKeyboardProc {

        /**
         * <p>
         * Specifies whether the event was injected. The value is 1 if that is the case; otherwise,
         * it is 0. Note that bit 1 is not necessarily set when bit 4 is set. <a href=
         * "https://msdn.microsoft.com/ja-jp/library/windows/desktop/ms644967(v=vs.85).aspx">REF</a>
         * </p>
         */
        private static final int InjectedEvent = 1 << 4;

        /** The key mapper. */
        private static final Key[] keys = new Key[250];

        static {
            for (Key key : Key.values()) {
                keys[key.virtualCode] = key;
            }
        }

        /** The actual executor. */
        private final ExecutorService executor = new ThreadPoolExecutor(4, 256, 30, TimeUnit.SECONDS, new SynchronousQueue(), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(NativeKeyboardHook.class.getSimpleName());
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.setDaemon(false);
            return thread;
        });

        /** The clean up. */
        private final Thread cleaner = new Thread(this::uninstall);

        /** The event listeners. */
        private final List<KeyMacro> presses = new ArrayList();

        /** The event listeners. */
        private final List<KeyMacro> releases = new ArrayList();

        /** The actual native hook. */
        private HHOOK nativeHook;

        /**
         * <p>
         * Install service.
         * </p>
         */
        private void install() {
            executor.execute(this);
            Runtime.getRuntime().addShutdownHook(cleaner);
        }

        /**
         * <p>
         * Uninstall service.
         * </p>
         */
        private void uninstall() {
            executor.shutdown();
            Runtime.getRuntime().removeShutdownHook(cleaner);
            User32.INSTANCE.UnhookWindowsHookEx(nativeHook);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void run() {
            nativeHook = User32.INSTANCE.SetWindowsHookEx(WinUser.WH_KEYBOARD_LL, this, Kernel32.INSTANCE.GetModuleHandle(null), 0);

            int result;
            MSG message = new MSG();

            while ((result = User32.INSTANCE.GetMessage(message, null, 0, 0)) != 0) {
                if (result == -1) {
                    break;
                } else {
                    User32.INSTANCE.TranslateMessage(message);
                    User32.INSTANCE.DispatchMessage(message);
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public LRESULT callback(int nCode, WPARAM wParam, KBDLLHOOKSTRUCT info) {
            boolean consumed = false;
            boolean userInput = (info.flags & InjectedEvent) == 0;

            if (0 <= nCode && userInput) {
                Key key = keys[info.vkCode];

                switch (wParam.intValue()) {
                case WinUser.WM_KEYDOWN:
                case WinUser.WM_SYSKEYDOWN:
                    consumed = handle(key, presses);
                    break;

                case WinUser.WM_KEYUP:
                case WinUser.WM_SYSKEYUP:
                    consumed = handle(key, releases);
                    break;
                }
            }
            return consumed ? new LRESULT(1)
                    : User32.INSTANCE.CallNextHookEx(nativeHook, nCode, wParam, new LPARAM(Pointer.nativeValue(info.getPointer())));
        }

        /**
         * <p>
         * Handle key event.
         * </p>
         * 
         * @param key
         */
        private boolean handle(Key key, List<KeyMacro> macros) {
            boolean consumed = false;

            if (!macros.isEmpty()) {
                Window now = Window.now();

                for (KeyMacro macro : macros) {
                    if (macro.window.test(now) && macro.condition.test(key)) {
                        executor.execute(macro.action);

                        if (macro.consumable) {
                            consumed = true;
                        }
                    }
                }
            }
            return consumed;
        }
    }
}
