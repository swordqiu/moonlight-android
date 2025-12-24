package com.limelight.binding.input.virtual_keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.limelight.LimeLog;
import com.limelight.binding.input.ControllerHandler;
import com.limelight.binding.input.KeyboardTranslator;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.nvstream.jni.MoonBridge;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Virtual keyboard overlay that emulates a physical keyboard with 101 keys in ANSI layout.
 * It captures touch events and translates them to keyboard input events sent via MoonBridge.
 */
public class VirtualKeyboard extends View {
    private static final boolean DEBUG = false;
    
    private final Context context;
    private final FrameLayout parentLayout;
    private final NvConnection connection;
    private final KeyboardTranslator keyboardTranslator;
    private final PreferenceConfiguration prefConfig;
    
    private List<VirtualKey> keys = new ArrayList<>();
    private byte modifierFlags = 0;
    private boolean isShiftSticky = false; // Sticky shift state
    private boolean isCapsLockActive = false; // CapsLock state
    private boolean isFnActive = false; // Fn modifier state
    private boolean isCtrlSticky = false; // Sticky Ctrl state
    private boolean isAltSticky = false; // Sticky Alt state
    private VirtualKey shiftLeftKey = null;
    private VirtualKey shiftRightKey = null;
    private VirtualKey capsLockKey = null;
    private VirtualKey fnKey = null;
    private VirtualKey ctrlLeftKey = null;
    private VirtualKey ctrlRightKey = null;
    private VirtualKey altLeftKey = null;
    private VirtualKey altRightKey = null;
    
    // Key layout configuration
    private int keyHeight;
    private int keySpacing;
    private int keyboardPadding;
    private int keyboardWidth;
    private int keyboardHeight;
    
    // ANSI keyboard layout - 101 keys arranged in rows
    // Row 1: Function keys (F1-F12)
    // Row 2: Number row with symbols
    // Row 3: QWERTY row
    // Row 4: ASDF row
    // Row 5: ZXCV row
    // Row 6: Space bar row
    
    private static class KeyDef {
        int androidKeyCode;
        String label;
        boolean isModifier;
        float widthMultiplier; // Multiplier for key width (1.0 = standard, 2.0 = double width, etc.)
        
        KeyDef(int androidKeyCode, String label, boolean isModifier, float widthMultiplier) {
            this.androidKeyCode = androidKeyCode;
            this.label = label;
            this.isModifier = isModifier;
            this.widthMultiplier = widthMultiplier;
        }
    }
    
    // Standard ANSI keyboard layout (without function keys and numpad)
    private static final KeyDef[][] KEYBOARD_LAYOUT = {
        // Row 1: Number row
        {new KeyDef(KeyEvent.KEYCODE_GRAVE, "`", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_1, "1", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_2, "2", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_3, "3", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_4, "4", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_5, "5", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_6, "6", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_7, "7", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_8, "8", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_9, "9", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_0, "0", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_MINUS, "-", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_EQUALS, "=", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_DEL, "⌫", false, 2.0f)}, // Backspace symbol
        
        // Row 2: QWERTY row
        {new KeyDef(KeyEvent.KEYCODE_TAB, "Tab⇥", false, 1.5f), // Tab symbol
         new KeyDef(KeyEvent.KEYCODE_Q, "q", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_W, "w", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_E, "e", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_R, "r", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_T, "t", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_Y, "y", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_U, "u", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_I, "i", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_O, "o", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_P, "p", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_LEFT_BRACKET, "[", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_RIGHT_BRACKET, "]", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_BACKSLASH, "\\", false, 1.5f)},
        
        // Row 3: ASDF row
        {new KeyDef(KeyEvent.KEYCODE_CAPS_LOCK, "Caps", true, 1.75f), // Caps Lock symbol
         new KeyDef(KeyEvent.KEYCODE_A, "a", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_S, "s", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_D, "d", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_F, "f", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_G, "g", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_H, "h", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_J, "j", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_K, "k", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_L, "l", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_SEMICOLON, ";", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_APOSTROPHE, "'", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_ENTER, "Enter↵", false, 2.25f)}, // Enter symbol
        
        // Row 4: ZXCV row
        {new KeyDef(KeyEvent.KEYCODE_SHIFT_LEFT, "Shift⇧", true, 2.25f), // Shift symbol
         new KeyDef(KeyEvent.KEYCODE_Z, "z", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_X, "x", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_C, "c", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_V, "v", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_B, "b", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_N, "n", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_M, "m", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_COMMA, ",", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_PERIOD, ".", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_SLASH, "/", false, 1.0f),
         new KeyDef(KeyEvent.KEYCODE_SHIFT_RIGHT, "Shift⇧", true, 2.75f)}, // Shift symbol
        
        // Row 5: Control row
        {new KeyDef(KeyEvent.KEYCODE_2, "@", false, 1.0f), // @ key at the beginning (uses KEYCODE_2 with shift)
         new KeyDef(KeyEvent.KEYCODE_CTRL_LEFT, "Ctrl⌃", true, 1.25f), // Ctrl symbol
         new KeyDef(KeyEvent.KEYCODE_ALT_LEFT, "Alt⌥", true, 1.25f), // Alt symbol
         new KeyDef(KeyEvent.KEYCODE_SPACE, "Space⎵", false, 5.25f), // Space symbol
         new KeyDef(KeyEvent.KEYCODE_ALT_RIGHT, "Alt⌥", true, 1.25f), // Alt symbol
         new KeyDef(KeyEvent.KEYCODE_MENU, "Fn", true, 1.25f), // Using MENU keycode for Fn key
         new KeyDef(KeyEvent.KEYCODE_CTRL_RIGHT, "Ctrl⌃", true, 1.25f), // Ctrl symbol
         new KeyDef(KeyEvent.KEYCODE_GRAVE, "~", false, 1.0f), // ~ key at the end (uses KEYCODE_GRAVE with shift)
         new KeyDef(KeyEvent.KEYCODE_SEMICOLON, ":", false, 1.0f)} // : key at the end
    };
    
    public VirtualKeyboard(Context context, FrameLayout parentLayout, NvConnection connection, PreferenceConfiguration prefConfig) {
        super(context);
        this.context = context;
        this.parentLayout = parentLayout;
        this.connection = connection;
        this.keyboardTranslator = new KeyboardTranslator();
        this.prefConfig = prefConfig;
        
        setBackgroundColor(0x80000000); // Semi-transparent dark background
        
        // Make keyboard focusable for gamepad navigation
        setFocusable(true);
        setFocusableInTouchMode(false);
        
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        keyHeight = (int)(metrics.heightPixels * 0.06f);
        keySpacing = (int)(metrics.density * 4);
        keyboardPadding = (int)(metrics.density * 4);
        
        createKeys();
    }
    
    private void createKeys() {
        keys.clear();
        
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        int availableWidth = screenWidth - (keyboardPadding * 2);
        
        // Calculate standard key width based on the widest row
        float maxRowWidth = 0;
        for (KeyDef[] row : KEYBOARD_LAYOUT) {
            float rowWidth = 0;
            for (KeyDef key : row) {
                rowWidth += key.widthMultiplier;
            }
            if (rowWidth > maxRowWidth) {
                maxRowWidth = rowWidth;
            }
        }
        
        float standardKeyWidth = (availableWidth - (KEYBOARD_LAYOUT[0].length - 1) * keySpacing) / maxRowWidth;
        
        // Calculate total keyboard height first (including ESC key row)
        int totalKeyboardHeight = KEYBOARD_LAYOUT.length * keyHeight + (KEYBOARD_LAYOUT.length - 1) * keySpacing + keyboardPadding * 2 + keyHeight + keySpacing;
        
        // Start from bottom of screen
        int currentY = screenHeight - totalKeyboardHeight + keyboardPadding;
        
        // Add ESC key above the first row, aligned to the left
        short escGfeKeyCode = keyboardTranslator.translate(KeyEvent.KEYCODE_ESCAPE, -1);
        if (escGfeKeyCode != 0) {
            VirtualKey escKey = new VirtualKey(this, context, KeyEvent.KEYCODE_ESCAPE, 
                                               escGfeKeyCode, "Esc", null, null, (short)0, false);
            int escKeyWidth = (int)(standardKeyWidth * 1.0f); // ESC key is slightly wider
            FrameLayout.LayoutParams escParams = new FrameLayout.LayoutParams(escKeyWidth, keyHeight);
            escParams.leftMargin = keyboardPadding;
            escParams.topMargin = currentY;
            parentLayout.addView(escKey, escParams);
            keys.add(escKey);
        }
        
        // Add Close key on the same row, aligned to the right
        // Use a special keycode that won't conflict with real keys (we'll handle it specially)
        VirtualKey closeKey = new VirtualKey(this, context, KeyEvent.KEYCODE_UNKNOWN, 
                                            (short)0, "✕", null, null, (short)0, false);
        int closeKeyWidth = (int)(standardKeyWidth * 1.0f); // Close key is slightly wider
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(closeKeyWidth, keyHeight);
        closeParams.leftMargin = screenWidth - keyboardPadding - closeKeyWidth;
        closeParams.topMargin = currentY;
        parentLayout.addView(closeKey, closeParams);
        keys.add(closeKey);
        
        // Move to the first regular row
        currentY += keyHeight + keySpacing;
        
        for (KeyDef[] row : KEYBOARD_LAYOUT) {
            // Calculate total width of this row
            float rowWidthMultiplier = 0;
            for (KeyDef keyDef : row) {
                rowWidthMultiplier += keyDef.widthMultiplier;
            }
            int totalRowWidth = (int)(standardKeyWidth * rowWidthMultiplier) + (row.length - 1) * keySpacing;
            
            // Calculate starting X position to center the row
            int currentX = (screenWidth - totalRowWidth) / 2;
            
            for (KeyDef keyDef : row) {
                int keyWidth = (int)(standardKeyWidth * keyDef.widthMultiplier);
                
                short gfeKeyCode = keyboardTranslator.translate(keyDef.androidKeyCode, -1);
                if (gfeKeyCode == 0) {
                    LimeLog.warning("VirtualKeyboard: Could not translate keycode " + keyDef.androidKeyCode);
                    continue;
                }
                
                // Get shifted label for keys that change with shift
                String shiftedLabel = getShiftedLabel(keyDef.androidKeyCode, keyDef.label);
                
                // Get Fn label and keycode for number row keys
                String fnLabel = getFnLabel(keyDef.androidKeyCode, keyDef.label);
                short fnGfeKeyCode = getFnGfeKeyCode(keyDef.androidKeyCode);
                
                VirtualKey key = new VirtualKey(this, context, keyDef.androidKeyCode, 
                                               gfeKeyCode, keyDef.label, shiftedLabel, fnLabel, fnGfeKeyCode, keyDef.isModifier);
                
                // Store references to modifier keys
                if (keyDef.androidKeyCode == KeyEvent.KEYCODE_SHIFT_LEFT) {
                    shiftLeftKey = key;
                } else if (keyDef.androidKeyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
                    shiftRightKey = key;
                } else if (keyDef.androidKeyCode == KeyEvent.KEYCODE_CAPS_LOCK) {
                    capsLockKey = key;
                } else if (keyDef.androidKeyCode == KeyEvent.KEYCODE_MENU) {
                    fnKey = key;
                }
                
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(keyWidth, keyHeight);
                params.leftMargin = currentX;
                params.topMargin = currentY;
                
                parentLayout.addView(key, params);
                keys.add(key);
                
                currentX += keyWidth + keySpacing;
            }
            
            currentY += keyHeight + keySpacing;
        }
        
        keyboardHeight = totalKeyboardHeight;
        keyboardWidth = screenWidth;
        
        // Set up gamepad navigation
        setupGamepadNavigation();
    }
    
    private void setupGamepadNavigation() {
        // Set up focus change listeners for all keys
        for (VirtualKey key : keys) {
            key.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    v.invalidate(); // Redraw to show/hide focus highlight
                }
            });
        }
    }
    
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        // Handle gamepad navigation and button presses
        if (event.getSource() == android.view.InputDevice.SOURCE_GAMEPAD ||
            event.getSource() == android.view.InputDevice.SOURCE_DPAD ||
            ControllerHandler.isGameControllerDevice(event.getDevice())) {
            
            VirtualKey focusedKey = getFocusedKey();
            
            switch (keyCode) {
                case android.view.KeyEvent.KEYCODE_DPAD_UP:
                    if (focusedKey != null) {
                        VirtualKey nextKey = findKeyAbove(focusedKey);
                        if (nextKey != null) {
                            nextKey.requestFocus();
                            return true;
                        }
                    }
                    break;
                    
                case android.view.KeyEvent.KEYCODE_DPAD_DOWN:
                    if (focusedKey != null) {
                        VirtualKey nextKey = findKeyBelow(focusedKey);
                        if (nextKey != null) {
                            nextKey.requestFocus();
                            return true;
                        }
                    }
                    break;
                    
                case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
                    if (focusedKey != null) {
                        VirtualKey nextKey = findKeyLeft(focusedKey);
                        if (nextKey != null) {
                            nextKey.requestFocus();
                            return true;
                        }
                    }
                    break;
                    
                case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (focusedKey != null) {
                        VirtualKey nextKey = findKeyRight(focusedKey);
                        if (nextKey != null) {
                            nextKey.requestFocus();
                            return true;
                        }
                    }
                    break;
                    
                case android.view.KeyEvent.KEYCODE_BUTTON_A:
                case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
                    // Press the focused key
                    if (focusedKey != null) {
                        focusedKey.setPressed(true);
                        onKeyDown(focusedKey, focusedKey.androidKeyCode, focusedKey.gfeKeyCode);
                        return true;
                    }
                    break;
            }
        }
        
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        // Handle gamepad button release
        if (event.getSource() == android.view.InputDevice.SOURCE_GAMEPAD ||
            event.getSource() == android.view.InputDevice.SOURCE_DPAD ||
            ControllerHandler.isGameControllerDevice(event.getDevice())) {
            
            VirtualKey focusedKey = getFocusedKey();
            
            if (keyCode == android.view.KeyEvent.KEYCODE_BUTTON_A ||
                keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER) {
                // Release the focused key
                if (focusedKey != null && focusedKey.getIsPressed()) {
                    focusedKey.setPressed(false);
                    onKeyUp(focusedKey, focusedKey.androidKeyCode, focusedKey.gfeKeyCode);
                    return true;
                }
            }
        }
        
        return super.onKeyUp(keyCode, event);
    }
    
    private VirtualKey getFocusedKey() {
        for (VirtualKey key : keys) {
            if (key.hasFocus()) {
                return key;
            }
        }
        return null;
    }
    
    private VirtualKey findKeyAbove(VirtualKey currentKey) {
        FrameLayout.LayoutParams currentParams = (FrameLayout.LayoutParams) currentKey.getLayoutParams();
        int currentTop = currentParams.topMargin;
        int currentLeft = currentParams.leftMargin;
        int currentRight = currentLeft + currentKey.getWidth();
        
        VirtualKey bestKey = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (VirtualKey key : keys) {
            if (key == currentKey) continue;
            
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) key.getLayoutParams();
            int keyTop = params.topMargin;
            int keyLeft = params.leftMargin;
            int keyRight = keyLeft + key.getWidth();
            
            // Key must be above current key
            if (keyTop < currentTop) {
                // Check if keys overlap horizontally
                if (!(keyRight < currentLeft || keyLeft > currentRight)) {
                    int distance = currentTop - keyTop;
                    if (distance < minDistance) {
                        minDistance = distance;
                        bestKey = key;
                    }
                }
            }
        }
        
        return bestKey;
    }
    
    private VirtualKey findKeyBelow(VirtualKey currentKey) {
        FrameLayout.LayoutParams currentParams = (FrameLayout.LayoutParams) currentKey.getLayoutParams();
        int currentTop = currentParams.topMargin;
        int currentBottom = currentTop + currentKey.getHeight();
        int currentLeft = currentParams.leftMargin;
        int currentRight = currentLeft + currentKey.getWidth();
        
        VirtualKey bestKey = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (VirtualKey key : keys) {
            if (key == currentKey) continue;
            
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) key.getLayoutParams();
            int keyTop = params.topMargin;
            int keyLeft = params.leftMargin;
            int keyRight = keyLeft + key.getWidth();
            
            // Key must be below current key
            if (keyTop > currentBottom) {
                // Check if keys overlap horizontally
                if (!(keyRight < currentLeft || keyLeft > currentRight)) {
                    int distance = keyTop - currentBottom;
                    if (distance < minDistance) {
                        minDistance = distance;
                        bestKey = key;
                    }
                }
            }
        }
        
        return bestKey;
    }
    
    private VirtualKey findKeyLeft(VirtualKey currentKey) {
        FrameLayout.LayoutParams currentParams = (FrameLayout.LayoutParams) currentKey.getLayoutParams();
        int currentLeft = currentParams.leftMargin;
        int currentTop = currentParams.topMargin;
        int currentBottom = currentTop + currentKey.getHeight();
        
        VirtualKey bestKey = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (VirtualKey key : keys) {
            if (key == currentKey) continue;
            
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) key.getLayoutParams();
            int keyRight = params.leftMargin + key.getWidth();
            int keyTop = params.topMargin;
            int keyBottom = keyTop + key.getHeight();
            
            // Key must be to the left of current key
            if (keyRight < currentLeft) {
                // Check if keys overlap vertically
                if (!(keyBottom < currentTop || keyTop > currentBottom)) {
                    int distance = currentLeft - keyRight;
                    if (distance < minDistance) {
                        minDistance = distance;
                        bestKey = key;
                    }
                }
            }
        }
        
        return bestKey;
    }
    
    private VirtualKey findKeyRight(VirtualKey currentKey) {
        FrameLayout.LayoutParams currentParams = (FrameLayout.LayoutParams) currentKey.getLayoutParams();
        int currentRight = currentParams.leftMargin + currentKey.getWidth();
        int currentTop = currentParams.topMargin;
        int currentBottom = currentTop + currentKey.getHeight();
        
        VirtualKey bestKey = null;
        int minDistance = Integer.MAX_VALUE;
        
        for (VirtualKey key : keys) {
            if (key == currentKey) continue;
            
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) key.getLayoutParams();
            int keyLeft = params.leftMargin;
            int keyTop = params.topMargin;
            int keyBottom = keyTop + key.getHeight();
            
            // Key must be to the right of current key
            if (keyLeft > currentRight) {
                // Check if keys overlap vertically
                if (!(keyBottom < currentTop || keyTop > currentBottom)) {
                    int distance = keyLeft - currentRight;
                    if (distance < minDistance) {
                        minDistance = distance;
                        bestKey = key;
                    }
                }
            }
        }
        
        return bestKey;
    }
    
    void onKeyDown(VirtualKey key, int androidKeyCode, short gfeKeyCode) {
        if (DEBUG) {
            LimeLog.info("VirtualKeyboard: Key down - " + key.getDisplayLabel(isShiftSticky, isCapsLockActive, isFnActive) + " (Android: " + androidKeyCode + ", GFE: " + gfeKeyCode + ")");
        }
        
        // Handle close key - hide keyboard instead of sending keycode
        if (androidKeyCode == KeyEvent.KEYCODE_UNKNOWN && key.getLabel() != null && key.getLabel().equals("✕")) {
            hideAndUpdatePreference();
            return; // Don't send any keycode, just hide the keyboard
        }
        
        // Handle sticky shift toggle
        if (androidKeyCode == KeyEvent.KEYCODE_SHIFT_LEFT || androidKeyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            toggleStickyShift();
            // Don't keep shift keys in pressed state from touch - they use sticky state instead
            if (shiftLeftKey != null) {
                shiftLeftKey.setPressed(false);
            }
            if (shiftRightKey != null) {
                shiftRightKey.setPressed(false);
            }
            return; // Don't send shift key event, just toggle state
        }
        
        // Handle CapsLock toggle
        if (androidKeyCode == KeyEvent.KEYCODE_CAPS_LOCK) {
            toggleCapsLock();
            // Don't keep caps lock key in pressed state from touch - it uses sticky state instead
            if (capsLockKey != null) {
                capsLockKey.setPressed(false);
            }
            return; // Don't send caps lock key event, just toggle state
        }
        
        // Handle Fn toggle
        if (androidKeyCode == KeyEvent.KEYCODE_MENU) {
            toggleFn();
            // Don't keep Fn key in pressed state from touch - it uses sticky state instead
            if (fnKey != null) {
                fnKey.setPressed(false);
            }
            return; // Don't send Fn key event, just toggle state
        }
        
        // Handle sticky Ctrl toggle
        if (androidKeyCode == KeyEvent.KEYCODE_CTRL_LEFT || androidKeyCode == KeyEvent.KEYCODE_CTRL_RIGHT) {
            toggleStickyCtrl();
            // Don't keep Ctrl keys in pressed state from touch - they use sticky state instead
            if (ctrlLeftKey != null) {
                ctrlLeftKey.setPressed(false);
            }
            if (ctrlRightKey != null) {
                ctrlRightKey.setPressed(false);
            }
            return; // Don't send Ctrl key event, just toggle state
        }
        
        // Handle sticky Alt toggle
        if (androidKeyCode == KeyEvent.KEYCODE_ALT_LEFT || androidKeyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            toggleStickyAlt();
            // Don't keep Alt keys in pressed state from touch - they use sticky state instead
            if (altLeftKey != null) {
                altLeftKey.setPressed(false);
            }
            if (altRightKey != null) {
                altRightKey.setPressed(false);
            }
            return; // Don't send Alt key event, just toggle state
        }
        
        // Calculate modifier flags including sticky modifiers
        byte currentModifierFlags = modifierFlags;
        
        // Add sticky Ctrl modifier
        if (isCtrlSticky) {
            currentModifierFlags |= KeyboardPacket.MODIFIER_CTRL;
        }
        
        // Add sticky Alt modifier
        if (isAltSticky) {
            currentModifierFlags |= KeyboardPacket.MODIFIER_ALT;
        }
        
        // Check if this is a letter key (A-Z)
        boolean isLetterKey = (androidKeyCode >= KeyEvent.KEYCODE_A && androidKeyCode <= KeyEvent.KEYCODE_Z);
        
        if (isLetterKey) {
            // For letter keys: Shift + CapsLock = lowercase (shift overrides caps lock)
            //                  Shift only = uppercase (add shift modifier)
            //                  CapsLock only = uppercase (add shift modifier)
            //                  Neither = lowercase (no shift modifier)
            if (isShiftSticky && isCapsLockActive) {
                // Both active: shift overrides caps lock, send lowercase (no shift modifier)
                // Don't add shift modifier
            } else if (isShiftSticky || isCapsLockActive) {
                // One active: send uppercase (add shift modifier)
                currentModifierFlags |= KeyboardPacket.MODIFIER_SHIFT;
            }
            // Neither active: send lowercase (no shift modifier)
        } else {
            // For non-letter keys: only shift affects them
            if (isShiftSticky) {
                currentModifierFlags |= KeyboardPacket.MODIFIER_SHIFT;
            }
        }
        
        // Special handling for ':' key - always send with shift modifier
        if ((androidKeyCode == KeyEvent.KEYCODE_SEMICOLON && key.getLabel() != null && key.getLabel().equals(":")) 
            || (androidKeyCode == KeyEvent.KEYCODE_GRAVE && key.getLabel() != null && key.getLabel().equals("~")) 
            || (androidKeyCode == KeyEvent.KEYCODE_2 && key.getLabel() != null && key.getLabel().equals("@"))) {
            currentModifierFlags |= KeyboardPacket.MODIFIER_SHIFT;
        }
        
        // Get the actual keycode to send (Fn keycode if Fn is active)
        short actualGfeKeyCode = key.getGfeKeyCode(isFnActive);
        
        // Send keyboard input with appropriate modifiers
        if (connection != null) {
            connection.sendKeyboardInput(actualGfeKeyCode, KeyboardPacket.KEY_DOWN, currentModifierFlags, (byte)0);
        }
    }
    
    void onKeyUp(VirtualKey key, int androidKeyCode, short gfeKeyCode) {
        if (DEBUG) {
            LimeLog.info("VirtualKeyboard: Key up - " + key.getDisplayLabel(isShiftSticky, isCapsLockActive, isFnActive) + " (Android: " + androidKeyCode + ", GFE: " + gfeKeyCode + ")");
        }
        
        // Shift keys, CapsLock, and Fn don't send key up events (they're sticky)
        if (androidKeyCode == KeyEvent.KEYCODE_SHIFT_LEFT || 
            androidKeyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            androidKeyCode == KeyEvent.KEYCODE_CAPS_LOCK ||
            androidKeyCode == KeyEvent.KEYCODE_MENU) {
            return;
        }
        
        // Calculate modifier flags including sticky modifiers
        byte currentModifierFlags = modifierFlags;
        
        // Add sticky Ctrl modifier
        if (isCtrlSticky) {
            currentModifierFlags |= KeyboardPacket.MODIFIER_CTRL;
        }
        
        // Add sticky Alt modifier
        if (isAltSticky) {
            currentModifierFlags |= KeyboardPacket.MODIFIER_ALT;
        }
        
        // Check if this is a letter key (A-Z)
        boolean isLetterKey = (androidKeyCode >= KeyEvent.KEYCODE_A && androidKeyCode <= KeyEvent.KEYCODE_Z);
        
        if (isLetterKey) {
            // For letter keys: Shift + CapsLock = lowercase (shift overrides caps lock)
            //                  Shift only = uppercase (add shift modifier)
            //                  CapsLock only = uppercase (add shift modifier)
            //                  Neither = lowercase (no shift modifier)
            if (isShiftSticky && isCapsLockActive) {
                // Both active: shift overrides caps lock, send lowercase (no shift modifier)
                // Don't add shift modifier
            } else if (isShiftSticky || isCapsLockActive) {
                // One active: send uppercase (add shift modifier)
                currentModifierFlags |= KeyboardPacket.MODIFIER_SHIFT;
            }
            // Neither active: send lowercase (no shift modifier)
        } else {
            // For non-letter keys: only shift affects them
            if (isShiftSticky) {
                currentModifierFlags |= KeyboardPacket.MODIFIER_SHIFT;
            }
        }
        
        // Special handling for '@' key - always send with shift modifier
        if (androidKeyCode == KeyEvent.KEYCODE_2 && key.getLabel() != null && key.getLabel().equals("@")) {
            currentModifierFlags |= KeyboardPacket.MODIFIER_SHIFT;
        }
        
        // Special handling for '@' key - always send with shift modifier
        if (androidKeyCode == KeyEvent.KEYCODE_2 && key.getLabel() != null && key.getLabel().equals("@")) {
            currentModifierFlags |= KeyboardPacket.MODIFIER_SHIFT;
        }
        
        // Special handling for ':' key - always send with shift modifier
        if ((androidKeyCode == KeyEvent.KEYCODE_SEMICOLON && key.getLabel() != null && key.getLabel().equals(":"))
            || (androidKeyCode == KeyEvent.KEYCODE_GRAVE && key.getLabel() != null && key.getLabel().equals("~"))
            || (androidKeyCode == KeyEvent.KEYCODE_2 && key.getLabel() != null && key.getLabel().equals("@"))) {
            currentModifierFlags |= KeyboardPacket.MODIFIER_SHIFT;
        }
        
        // Get the actual keycode to send (Fn keycode if Fn is active)
        short actualGfeKeyCode = key.getGfeKeyCode(isFnActive);
        
        // Send keyboard input with appropriate modifiers
        if (connection != null) {
            connection.sendKeyboardInput(actualGfeKeyCode, KeyboardPacket.KEY_UP, currentModifierFlags, (byte)0);
        }
    }
    
    private void updateModifierFlags(int androidKeyCode, boolean pressed) {
        // Sticky modifier keys (Shift, Ctrl, Alt) are handled separately, so skip them here
        if (androidKeyCode == KeyEvent.KEYCODE_SHIFT_LEFT || 
            androidKeyCode == KeyEvent.KEYCODE_SHIFT_RIGHT ||
            androidKeyCode == KeyEvent.KEYCODE_CTRL_LEFT ||
            androidKeyCode == KeyEvent.KEYCODE_CTRL_RIGHT ||
            androidKeyCode == KeyEvent.KEYCODE_ALT_LEFT ||
            androidKeyCode == KeyEvent.KEYCODE_ALT_RIGHT) {
            return;
        }
        
        // No other modifiers to handle (Win keys removed)
    }
    
    private String getShiftedLabel(int androidKeyCode, String normalLabel) {
        // Map normal labels to their shifted equivalents
        switch (androidKeyCode) {
            // Number row shifted symbols
            case KeyEvent.KEYCODE_GRAVE: return "~";
            case KeyEvent.KEYCODE_1: return "!";
            case KeyEvent.KEYCODE_2: return "@";
            case KeyEvent.KEYCODE_3: return "#";
            case KeyEvent.KEYCODE_4: return "$";
            case KeyEvent.KEYCODE_5: return "%";
            case KeyEvent.KEYCODE_6: return "^";
            case KeyEvent.KEYCODE_7: return "&";
            case KeyEvent.KEYCODE_8: return "*";
            case KeyEvent.KEYCODE_9: return "(";
            case KeyEvent.KEYCODE_0: return ")";
            case KeyEvent.KEYCODE_MINUS: return "_";
            case KeyEvent.KEYCODE_EQUALS: return "+";
            
            // QWERTY row shifted symbols
            case KeyEvent.KEYCODE_LEFT_BRACKET: return "{";
            case KeyEvent.KEYCODE_RIGHT_BRACKET: return "}";
            case KeyEvent.KEYCODE_BACKSLASH: return "|";
            
            // ASDF row shifted symbols
            case KeyEvent.KEYCODE_SEMICOLON: return ":";
            case KeyEvent.KEYCODE_APOSTROPHE: return "\"";
            
            // ZXCV row shifted symbols
            case KeyEvent.KEYCODE_COMMA: return "<";
            case KeyEvent.KEYCODE_PERIOD: return ">";
            case KeyEvent.KEYCODE_SLASH: return "?";
            
            // Letters - uppercase
            case KeyEvent.KEYCODE_A: return "A";
            case KeyEvent.KEYCODE_B: return "B";
            case KeyEvent.KEYCODE_C: return "C";
            case KeyEvent.KEYCODE_D: return "D";
            case KeyEvent.KEYCODE_E: return "E";
            case KeyEvent.KEYCODE_F: return "F";
            case KeyEvent.KEYCODE_G: return "G";
            case KeyEvent.KEYCODE_H: return "H";
            case KeyEvent.KEYCODE_I: return "I";
            case KeyEvent.KEYCODE_J: return "J";
            case KeyEvent.KEYCODE_K: return "K";
            case KeyEvent.KEYCODE_L: return "L";
            case KeyEvent.KEYCODE_M: return "M";
            case KeyEvent.KEYCODE_N: return "N";
            case KeyEvent.KEYCODE_O: return "O";
            case KeyEvent.KEYCODE_P: return "P";
            case KeyEvent.KEYCODE_Q: return "Q";
            case KeyEvent.KEYCODE_R: return "R";
            case KeyEvent.KEYCODE_S: return "S";
            case KeyEvent.KEYCODE_T: return "T";
            case KeyEvent.KEYCODE_U: return "U";
            case KeyEvent.KEYCODE_V: return "V";
            case KeyEvent.KEYCODE_W: return "W";
            case KeyEvent.KEYCODE_X: return "X";
            case KeyEvent.KEYCODE_Y: return "Y";
            case KeyEvent.KEYCODE_Z: return "Z";
            
            default: return null; // No shifted label
        }
    }
    
    private void toggleStickyShift() {
        isShiftSticky = !isShiftSticky;
        
        // Update visual appearance of shift keys - invalidate to trigger redraw with new state
        if (shiftLeftKey != null) {
            shiftLeftKey.invalidate();
        }
        if (shiftRightKey != null) {
            shiftRightKey.invalidate();
        }
        
        // Update all key labels to reflect shift state
        for (VirtualKey key : keys) {
            key.updateLabels(isShiftSticky, isCapsLockActive, isFnActive);
        }
        
        if (DEBUG) {
            LimeLog.info("VirtualKeyboard: Sticky shift " + (isShiftSticky ? "enabled" : "disabled"));
        }
    }
    
    private void toggleCapsLock() {
        isCapsLockActive = !isCapsLockActive;
        
        // Update visual appearance of CapsLock key - invalidate to trigger redraw with new state
        if (capsLockKey != null) {
            capsLockKey.invalidate();
        }
        
        // Update all key labels to reflect caps lock state
        for (VirtualKey key : keys) {
            key.updateLabels(isShiftSticky, isCapsLockActive, isFnActive);
        }
        
        if (DEBUG) {
            LimeLog.info("VirtualKeyboard: CapsLock " + (isCapsLockActive ? "enabled" : "disabled"));
        }
    }
    
    private void toggleFn() {
        isFnActive = !isFnActive;
        
        // Update visual appearance of Fn key - invalidate to trigger redraw with new state
        if (fnKey != null) {
            fnKey.invalidate();
        }
        
        // Update all key labels to reflect Fn state
        for (VirtualKey key : keys) {
            key.updateLabels(isShiftSticky, isCapsLockActive, isFnActive);
        }
        
        if (DEBUG) {
            LimeLog.info("VirtualKeyboard: Fn " + (isFnActive ? "enabled" : "disabled"));
        }
    }
    
    private void toggleStickyCtrl() {
        isCtrlSticky = !isCtrlSticky;
        
        // Update visual appearance of Ctrl keys - invalidate to trigger redraw with new state
        if (ctrlLeftKey != null) {
            ctrlLeftKey.invalidate();
        }
        if (ctrlRightKey != null) {
            ctrlRightKey.invalidate();
        }
        
        if (DEBUG) {
            LimeLog.info("VirtualKeyboard: Sticky Ctrl " + (isCtrlSticky ? "enabled" : "disabled"));
        }
    }
    
    private void toggleStickyAlt() {
        isAltSticky = !isAltSticky;
        
        // Update visual appearance of Alt keys - invalidate to trigger redraw with new state
        if (altLeftKey != null) {
            altLeftKey.invalidate();
        }
        if (altRightKey != null) {
            altRightKey.invalidate();
        }
        
        if (DEBUG) {
            LimeLog.info("VirtualKeyboard: Sticky Alt " + (isAltSticky ? "enabled" : "disabled"));
        }
    }
    
    private String getFnLabel(int androidKeyCode, String normalLabel) {
        // Map number row keys to function keys when Fn is active
        switch (androidKeyCode) {
            case KeyEvent.KEYCODE_1: return "F1";
            case KeyEvent.KEYCODE_2: return "F2";
            case KeyEvent.KEYCODE_3: return "F3";
            case KeyEvent.KEYCODE_4: return "F4";
            case KeyEvent.KEYCODE_5: return "F5";
            case KeyEvent.KEYCODE_6: return "F6";
            case KeyEvent.KEYCODE_7: return "F7";
            case KeyEvent.KEYCODE_8: return "F8";
            case KeyEvent.KEYCODE_9: return "F9";
            case KeyEvent.KEYCODE_0: return "F10";
            case KeyEvent.KEYCODE_MINUS: return "F11";
            case KeyEvent.KEYCODE_EQUALS: return "F12";
            default: return null; // No Fn label
        }
    }
    
    private short getFnGfeKeyCode(int androidKeyCode) {
        // Map number row keys to function key codes when Fn is active
        short gfeKeyCode = keyboardTranslator.translate(androidKeyCode, -1);
        if (gfeKeyCode == 0) {
            return 0;
        }
        
        switch (androidKeyCode) {
            case KeyEvent.KEYCODE_1: {
                short f1Code = keyboardTranslator.translate(KeyEvent.KEYCODE_F1, -1);
                return f1Code != 0 ? f1Code : gfeKeyCode;
            }
            case KeyEvent.KEYCODE_2: {
                short f2Code = keyboardTranslator.translate(KeyEvent.KEYCODE_F2, -1);
                return f2Code != 0 ? f2Code : gfeKeyCode;
            }
            case KeyEvent.KEYCODE_3: {
                short f3Code = keyboardTranslator.translate(KeyEvent.KEYCODE_F3, -1);
                return f3Code != 0 ? f3Code : gfeKeyCode;
            }
            case KeyEvent.KEYCODE_4: {
                short f4Code = keyboardTranslator.translate(KeyEvent.KEYCODE_F4, -1);
                return f4Code != 0 ? f4Code : gfeKeyCode;
            }
            case KeyEvent.KEYCODE_5: {
                short f5Code = keyboardTranslator.translate(KeyEvent.KEYCODE_F5, -1);
                return f5Code != 0 ? f5Code : gfeKeyCode;
            }
            case KeyEvent.KEYCODE_6: {
                short f6Code = keyboardTranslator.translate(KeyEvent.KEYCODE_F6, -1);
                return f6Code != 0 ? f6Code : gfeKeyCode;
            }
            case KeyEvent.KEYCODE_7: {
                short f7Code = keyboardTranslator.translate(KeyEvent.KEYCODE_F7, -1);
                return f7Code != 0 ? f7Code : gfeKeyCode;
            }
            case KeyEvent.KEYCODE_8: {
                short f8Code = keyboardTranslator.translate(KeyEvent.KEYCODE_F8, -1);
                return f8Code != 0 ? f8Code : gfeKeyCode;
            }
            case KeyEvent.KEYCODE_9: {
                short f9Code = keyboardTranslator.translate(KeyEvent.KEYCODE_F9, -1);
                return f9Code != 0 ? f9Code : gfeKeyCode;
            }
            case KeyEvent.KEYCODE_0: {
                short f10Code = keyboardTranslator.translate(KeyEvent.KEYCODE_F10, -1);
                return f10Code != 0 ? f10Code : gfeKeyCode;
            }
            case KeyEvent.KEYCODE_MINUS: {
                short f11Code = keyboardTranslator.translate(KeyEvent.KEYCODE_F11, -1);
                return f11Code != 0 ? f11Code : gfeKeyCode;
            }
            case KeyEvent.KEYCODE_EQUALS: {
                short f12Code = keyboardTranslator.translate(KeyEvent.KEYCODE_F12, -1);
                return f12Code != 0 ? f12Code : gfeKeyCode;
            }
            default: return gfeKeyCode; // No Fn transformation
        }
    }
    
    boolean isShiftSticky() {
        return isShiftSticky;
    }
    
    boolean isCapsLockActive() {
        return isCapsLockActive;
    }
    
    boolean isFnActive() {
        return isFnActive;
    }
    
    boolean isCtrlSticky() {
        return isCtrlSticky;
    }
    
    boolean isAltSticky() {
        return isAltSticky;
    }
    
    public void show() {
        setVisibility(View.VISIBLE);
        for (VirtualKey key : keys) {
            key.setVisibility(View.VISIBLE);
        }
        // Set initial focus on the ESC key for gamepad navigation
        focusEscKey();
    }
    
    private void focusEscKey() {
        // Find the ESC key and set focus on it
        for (VirtualKey key : keys) {
            if (key.androidKeyCode == KeyEvent.KEYCODE_ESCAPE) {
                key.requestFocus();
                return;
            }
        }
        // If ESC key not found, fall back to first key
        if (!keys.isEmpty()) {
            keys.get(0).requestFocus();
        }
    }
    
    public void hide() {
        setVisibility(View.GONE);
        for (VirtualKey key : keys) {
            key.setVisibility(View.GONE);
            // Release any pressed keys
            if (key.getIsPressed()) {
                key.setPressed(false);
                onKeyUp(key, key.androidKeyCode, key.gfeKeyCode);
            }
        }
        // Reset modifier flags and sticky states when hiding
        modifierFlags = 0;
        isShiftSticky = false;
        isCapsLockActive = false;
        isFnActive = false;
        isCtrlSticky = false;
        isAltSticky = false;
    }
    
    private void hideAndUpdatePreference() {
        // Update preference before hiding
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean("checkbox_show_virtual_keyboard", false).apply();
        prefConfig.onscreenKeyboard = false;
        
        // Hide the keyboard
        hide();
    }
    
    public void refreshLayout() {
        // Remove all keys
        for (VirtualKey key : keys) {
            parentLayout.removeView(key);
        }
        keys.clear();
        
        // Recreate keys
        createKeys();
    }
    
    public boolean isVisible() {
        return getVisibility() == View.VISIBLE;
    }
}
