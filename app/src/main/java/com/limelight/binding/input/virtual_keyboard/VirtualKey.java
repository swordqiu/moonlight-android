package com.limelight.binding.input.virtual_keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * A single virtual keyboard key that can be pressed to send keyboard input.
 */
public class VirtualKey extends View {
    private static final boolean DEBUG = false;
    
    private final VirtualKeyboard virtualKeyboard;
    final int androidKeyCode;
    final short gfeKeyCode;
    private String label;
    private String shiftedLabel; // Label when shift is active
    private String fnLabel; // Label when Fn is active
    private short fnGfeKeyCode; // GFE keycode when Fn is active
    private final boolean isModifier;
    
    private boolean isPressed = false;
    private final Paint paint = new Paint();
    private final RectF rect = new RectF();
    
    private static final int NORMAL_COLOR = 0x20888888; // More transparent (was 0xE0, now 0x80)
    private static final int PRESSED_COLOR = 0x30FF0000; // More transparent when pressed
    private static final int FOCUSED_COLOR = 0x4042A5F5; // Blue highlight when focused
    private static final int TEXT_COLOR = 0xEEEEEEEE;
    private static final int BORDER_COLOR = 0x80EEEEEE;
    private static final int FOCUSED_BORDER_COLOR = 0xFF42A5F5; // Brighter border when focused
    
    public VirtualKey(VirtualKeyboard keyboard, Context context, int androidKeyCode, 
                     short gfeKeyCode, String label, boolean isModifier) {
        this(keyboard, context, androidKeyCode, gfeKeyCode, label, null, isModifier);
    }
    
    public VirtualKey(VirtualKeyboard keyboard, Context context, int androidKeyCode, 
                     short gfeKeyCode, String label, String shiftedLabel, boolean isModifier) {
        this(keyboard, context, androidKeyCode, gfeKeyCode, label, shiftedLabel, null, gfeKeyCode, isModifier);
    }
    
    public VirtualKey(VirtualKeyboard keyboard, Context context, int androidKeyCode, 
                     short gfeKeyCode, String label, String shiftedLabel, String fnLabel, 
                     short fnGfeKeyCode, boolean isModifier) {
        super(context);
        this.virtualKeyboard = keyboard;
        this.androidKeyCode = androidKeyCode;
        this.gfeKeyCode = gfeKeyCode;
        this.label = label;
        this.shiftedLabel = shiftedLabel;
        this.fnLabel = fnLabel;
        this.fnGfeKeyCode = fnGfeKeyCode;
        this.isModifier = isModifier;
        
        // Make key focusable for gamepad navigation
        setFocusable(true);
        setFocusableInTouchMode(false);
        
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.CENTER);
    }
    
    public void updateLabels(boolean isShiftActive, boolean isCapsLockActive, boolean isFnActive) {
        // This will be called to update the display label
        invalidate();
    }
    
    public String getLabel() {
        return label;
    }
    
    public String getDisplayLabel(boolean isShiftActive, boolean isCapsLockActive, boolean isFnActive) {
        // Fn has highest priority: if Fn is active and this key has an Fn label, show it
        if (isFnActive && fnLabel != null) {
            return fnLabel;
        }
        
        // For letters: Shift + CapsLock = lowercase (shift overrides caps lock)
        //              Shift only = uppercase
        //              CapsLock only = uppercase
        //              Neither = lowercase
        if (isLetterKey()) {
            if (isShiftActive && isCapsLockActive) {
                // Both active: shift overrides caps lock, show lowercase
                return label;
            } else if (isShiftActive) {
                // Shift only: show uppercase
                return shiftedLabel != null ? shiftedLabel : label.toUpperCase();
            } else if (isCapsLockActive) {
                // CapsLock only: show uppercase
                return shiftedLabel != null ? shiftedLabel : label.toUpperCase();
            } else {
                // Normal state: show lowercase
                return label;
            }
        }
        
        // For non-letter keys: Shift shows shifted symbols, otherwise normal
        if (isShiftActive && shiftedLabel != null) {
            return shiftedLabel;
        }
        
        return label;
    }
    
    public short getGfeKeyCode(boolean isFnActive) {
        // Return Fn keycode if Fn is active and this key has an Fn keycode
        if (isFnActive && fnLabel != null && fnGfeKeyCode != 0) {
            return fnGfeKeyCode;
        }
        return gfeKeyCode;
    }
    
    private boolean isLetterKey() {
        // Check if this is an alphabetic key (A-Z)
        return (androidKeyCode >= android.view.KeyEvent.KEYCODE_A && 
                androidKeyCode <= android.view.KeyEvent.KEYCODE_Z);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        
        // For modifier keys, use sticky state instead of touch pressed state
        boolean shouldShowPressed = isPressed;
        if (isModifier && virtualKeyboard != null) {
            if (androidKeyCode == android.view.KeyEvent.KEYCODE_SHIFT_LEFT || 
                androidKeyCode == android.view.KeyEvent.KEYCODE_SHIFT_RIGHT) {
                shouldShowPressed = virtualKeyboard.isShiftSticky();
            } else if (androidKeyCode == android.view.KeyEvent.KEYCODE_CAPS_LOCK) {
                shouldShowPressed = virtualKeyboard.isCapsLockActive();
            } else if (androidKeyCode == android.view.KeyEvent.KEYCODE_MENU) {
                shouldShowPressed = virtualKeyboard.isFnActive();
            } else if (androidKeyCode == android.view.KeyEvent.KEYCODE_CTRL_LEFT ||
                       androidKeyCode == android.view.KeyEvent.KEYCODE_CTRL_RIGHT) {
                shouldShowPressed = virtualKeyboard.isCtrlSticky();
            } else if (androidKeyCode == android.view.KeyEvent.KEYCODE_ALT_LEFT ||
                       androidKeyCode == android.view.KeyEvent.KEYCODE_ALT_RIGHT) {
                shouldShowPressed = virtualKeyboard.isAltSticky();
            }
        }
        
        // Check if key is focused
        boolean isFocused = hasFocus();
        
        // Draw key background with rounded corners
        paint.setStyle(Paint.Style.FILL);
        if (shouldShowPressed) {
            paint.setColor(PRESSED_COLOR);
        } else if (isFocused) {
            paint.setColor(FOCUSED_COLOR);
        } else {
            paint.setColor(NORMAL_COLOR);
        }
        float cornerRadius = Math.min(getWidth(), getHeight()) * 0.25f; // Larger corner radius
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        
        // Draw key border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(isFocused ? 3 : 2); // Thicker border when focused
        paint.setColor(isFocused ? FOCUSED_BORDER_COLOR : BORDER_COLOR);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
        
        // Draw key label with larger font
        String displayLabel = virtualKeyboard != null ? 
            getDisplayLabel(virtualKeyboard.isShiftSticky(), virtualKeyboard.isCapsLockActive(), virtualKeyboard.isFnActive()) : label;
        if (displayLabel != null && !displayLabel.isEmpty()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(TEXT_COLOR);
            paint.setTextSize(Math.min(getWidth(), getHeight()) * 0.5f); // Increased from 0.3f to 0.5f
            paint.setFakeBoldText(isModifier);
            
            float textY = getHeight() / 2f + paint.getTextSize() / 3f;
            canvas.drawText(displayLabel, getWidth() / 2f, textY, paint);
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionIndex() != 0) {
            return true; // Ignore secondary touches
        }
        
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!isPressed) {
                    isPressed = true;
                    invalidate();
                    virtualKeyboard.onKeyDown(this, androidKeyCode, gfeKeyCode);
                }
                return true;
                
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isPressed) {
                    isPressed = false;
                    invalidate();
                    virtualKeyboard.onKeyUp(this, androidKeyCode, gfeKeyCode);
                }
                return true;
                
            case MotionEvent.ACTION_MOVE:
                // Check if touch moved outside key bounds
                float x = event.getX();
                float y = event.getY();
                boolean inBounds = x >= 0 && x <= getWidth() && y >= 0 && y <= getHeight();
                
                if (isPressed && !inBounds) {
                    isPressed = false;
                    invalidate();
                    virtualKeyboard.onKeyUp(this, androidKeyCode, gfeKeyCode);
                } else if (!isPressed && inBounds) {
                    isPressed = true;
                    invalidate();
                    virtualKeyboard.onKeyDown(this, androidKeyCode, gfeKeyCode);
                }
                return true;
        }
        
        return super.onTouchEvent(event);
    }
    
    public boolean getIsPressed() {
        return isPressed;
    }
    
    public void setPressed(boolean pressed) {
        if (isPressed != pressed) {
            isPressed = pressed;
            invalidate();
        }
    }
}
