package com.zteam.callapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_CALL_PHONE = 1001;
    private static final int REQ_READ_CONTACTS = 1002;
    private static final String PREFS = "call_app_prefs";
    private static final String PREF_SELECTED_SIM_INDEX = "selected_sim_index";

    private final StringBuilder typedNumber = new StringBuilder();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView numberView;
    private TextView selectedContactView;
    private LinearLayout suggestionsRow;
    private Button callButton;
    private SharedPreferences prefs;
    private List<PhoneAccountHandle> phoneAccounts = new ArrayList<>();
    private PhoneAccountHandle selectedPhoneAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        configureSystemBars();
        refreshPhoneAccounts();
        setContentView(createContentView());
        updateUi();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private View createContentView() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.WHITE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        frame.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        suggestionsRow = new LinearLayout(this);
        suggestionsRow.setOrientation(LinearLayout.HORIZONTAL);
        suggestionsRow.setGravity(Gravity.CENTER_VERTICAL);
        HorizontalScrollView suggestionsScroll = new HorizontalScrollView(this);
        suggestionsScroll.setHorizontalScrollBarEnabled(false);
        suggestionsScroll.addView(suggestionsRow);
        root.addView(suggestionsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(72)
        ));

        numberView = new TextView(this);
        numberView.setGravity(Gravity.CENTER);
        numberView.setTextSize(30);
        numberView.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        numberView.setTextColor(Color.rgb(30, 30, 30));
        numberView.setSingleLine(true);
        numberView.setEllipsize(TextUtils.TruncateAt.START);
        root.addView(numberView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ));

        selectedContactView = new TextView(this);
        selectedContactView.setGravity(Gravity.CENTER);
        selectedContactView.setTextSize(14);
        selectedContactView.setTextColor(Color.rgb(120, 120, 120));
        root.addView(selectedContactView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(28)
        ));

        SpaceView topSpace = new SpaceView(this);
        root.addView(topSpace, new LinearLayout.LayoutParams(1, 0, 1.15f));

        GridLayout keypad = new GridLayout(this);
        keypad.setColumnCount(3);
        keypad.setRowCount(4);
        keypad.setUseDefaultMargins(false);
        keypad.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        String[][] keys = {
                {"1", ""}, {"2", "ABC"}, {"3", "DEF"},
                {"4", "GHI"}, {"5", "JKL"}, {"6", "MNO"},
                {"7", "PQRS"}, {"8", "TUV"}, {"9", "WXYZ"},
                {"*", ""}, {"0", "+"}, {"#", ""}
        };
        for (String[] key : keys) {
            keypad.addView(createKeyButton(key[0], key[1]));
        }
        LinearLayout.LayoutParams keypadParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        keypadParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(keypad, keypadParams);

        LinearLayout callRow = new LinearLayout(this);
        callRow.setGravity(Gravity.CENTER);
        callRow.setOrientation(LinearLayout.HORIZONTAL);
        callRow.setPadding(0, dp(12), 0, 0);

        callButton = new Button(this);
        callButton.setText("☎");
        callButton.setTextSize(25);
        callButton.setTextColor(Color.WHITE);
        callButton.setAllCaps(false);
        callButton.setBackground(round(Color.rgb(52, 199, 89), dp(56)));
        callButton.setOnClickListener(v -> beginCallFlow());
        callRow.addView(callButton, new LinearLayout.LayoutParams(dp(56), dp(56)));
        root.addView(callRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(78)
        ));

        SpaceView bottomSpace = new SpaceView(this);
        root.addView(bottomSpace, new LinearLayout.LayoutParams(1, 0, 0.75f));

        LinearLayout nav = createBottomNavigation();
        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(58),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
        );
        navParams.setMargins(0, 0, 0, dp(16));
        frame.addView(nav, navParams);

        return frame;
    }

    private Button createKeyButton(String number, String letters) {
        Button button = new Button(this);
        String label = letters.isEmpty() ? number : number + "\n" + letters;
        button.setText(label);
        button.setTextColor(Color.BLACK);
        button.setTextSize(letters.isEmpty() ? 24 : 18);
        button.setGravity(Gravity.CENTER);
        button.setAllCaps(false);
        button.setIncludeFontPadding(false);
        button.setBackground(round(Color.rgb(236, 236, 238), dp(58)));
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = dp(58);
        params.height = dp(58);
        params.setMargins(dp(8), dp(8), dp(8), dp(8));
        button.setLayoutParams(params);
        button.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if ("0".equals(number) && typedNumber.length() == 0) {
                typedNumber.append("0");
            } else {
                typedNumber.append(number);
            }
            updateUi();
        });
        button.setOnLongClickListener(v -> {
            if ("0".equals(number)) {
                typedNumber.append("+");
                updateUi();
                return true;
            }
            return false;
        });
        return button;
    }

    private LinearLayout createBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(8), dp(5), dp(8), dp(5));
        nav.setBackground(round(Color.rgb(247, 247, 248), dp(28)));
        nav.setElevation(dp(10));

        nav.addView(navItem("◔\nCalls", false));
        nav.addView(navItem("●\nContacts", false));
        nav.addView(navItem("▦\nKeypad", true));
        nav.addView(navItem("⌕\nSearch", false));
        return nav;
    }

    private TextView navItem(String text, boolean selected) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setGravity(Gravity.CENTER);
        item.setTextSize(10);
        item.setTextColor(selected ? Color.rgb(0, 122, 255) : Color.rgb(20, 20, 20));
        item.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        item.setBackground(selected ? round(Color.rgb(232, 241, 255), dp(22)) : null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.MATCH_PARENT);
        lp.setMargins(dp(2), 0, dp(2), 0);
        item.setLayoutParams(lp);
        return item;
    }

    private void updateUi() {
        String value = typedNumber.toString();
        numberView.setText(value);
        numberView.setVisibility(value.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        callButton.setAlpha(value.trim().isEmpty() ? 0.45f : 1f);
        selectedContactView.setText("");

        if (value.length() >= 2) {
            if (hasPermission(Manifest.permission.READ_CONTACTS)) {
                loadContactSuggestions(value);
            } else {
                suggestionsRow.removeAllViews();
                addPermissionChip();
            }
        } else {
            suggestionsRow.removeAllViews();
        }
    }

    private void addPermissionChip() {
        TextView chip = new TextView(this);
        chip.setText("برای نمایش پیشنهاد مخاطبین، دسترسی مخاطبین را فعال کن");
        chip.setTextSize(12);
        chip.setTextColor(Color.rgb(70, 70, 70));
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(14), 0, dp(14), 0);
        chip.setBackground(round(Color.rgb(245, 245, 247), dp(22)));
        chip.setOnClickListener(v -> requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_READ_CONTACTS));
        suggestionsRow.addView(chip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
        ));
    }

    private void loadContactSuggestions(String input) {
        final String queryDigits = normalizePhone(input);
        executor.execute(() -> {
            List<ContactSuggestion> results = findContacts(queryDigits);
            mainHandler.post(() -> showSuggestions(results));
        });
    }

    private List<ContactSuggestion> findContacts(String queryDigits) {
        List<ContactSuggestion> results = new ArrayList<>();
        if (queryDigits.length() < 2) return results;

        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        };

        try (Cursor cursor = getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor == null) return results;
            int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            int photoIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI);

            while (cursor.moveToNext() && results.size() < 6) {
                String name = cursor.getString(nameIndex);
                String number = cursor.getString(numberIndex);
                String photo = photoIndex >= 0 ? cursor.getString(photoIndex) : null;
                String normalized = normalizePhone(number);
                if (normalized.contains(queryDigits)) {
                    results.add(new ContactSuggestion(name, number, photo));
                }
            }
        } catch (Exception ignored) {
            // Do not crash on contact-provider differences. No sensitive data should be logged here.
        }
        return results;
    }

    private void showSuggestions(List<ContactSuggestion> suggestions) {
        suggestionsRow.removeAllViews();
        for (ContactSuggestion suggestion : suggestions) {
            suggestionsRow.addView(createContactChip(suggestion));
        }
    }

    private View createContactChip(ContactSuggestion suggestion) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(8), 0, dp(12), 0);
        chip.setBackground(round(Color.rgb(246, 246, 247), dp(24)));

        TextView avatar = new TextView(this);
        avatar.setText(initials(suggestion.name));
        avatar.setTextColor(Color.WHITE);
        avatar.setTextSize(13);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        avatar.setBackground(round(Color.rgb(0, 122, 255), dp(34)));
        chip.addView(avatar, new LinearLayout.LayoutParams(dp(34), dp(34)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(8), 0, 0, 0);

        TextView name = new TextView(this);
        name.setText(nonEmpty(suggestion.name, "Unknown"));
        name.setTextColor(Color.rgb(30, 30, 30));
        name.setTextSize(13);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(name);

        TextView number = new TextView(this);
        number.setText(suggestion.number);
        number.setTextColor(Color.rgb(120, 120, 120));
        number.setTextSize(11);
        number.setSingleLine(true);
        number.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(number);

        chip.addView(texts, new LinearLayout.LayoutParams(dp(125), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(52)
        );
        params.setMargins(0, dp(10), dp(8), dp(10));
        chip.setLayoutParams(params);
        chip.setOnClickListener(v -> {
            typedNumber.setLength(0);
            typedNumber.append(normalizeVisiblePhone(suggestion.number));
            selectedContactView.setText(suggestion.name);
            suggestionsRow.removeAllViews();
            updateUi();
        });
        return chip;
    }

    private void beginCallFlow() {
        String number = typedNumber.toString().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "شماره‌ای وارد نشده است", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!hasPermission(Manifest.permission.CALL_PHONE)) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, REQ_CALL_PHONE);
            return;
        }
        refreshPhoneAccounts();
        if (phoneAccounts.size() > 1) {
            showSimSelector(number);
        } else {
            placeCall(number, phoneAccounts.isEmpty() ? null : phoneAccounts.get(0));
        }
    }

    private void refreshPhoneAccounts() {
        phoneAccounts = new ArrayList<>();
        try {
            TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (telecomManager != null && hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                phoneAccounts.addAll(telecomManager.getCallCapablePhoneAccounts());
                int savedIndex = prefs.getInt(PREF_SELECTED_SIM_INDEX, -1);
                if (savedIndex >= 0 && savedIndex < phoneAccounts.size()) {
                    selectedPhoneAccount = phoneAccounts.get(savedIndex);
                }
            } else if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, REQ_CALL_PHONE);
            }
        } catch (Exception ignored) {
            // Multi-SIM APIs vary by device/OEM. Fallback is normal call intent.
        }
    }

    private void showSimSelector(String number) {
        String[] labels = new String[phoneAccounts.size()];
        TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
        for (int i = 0; i < phoneAccounts.size(); i++) {
            CharSequence label = null;
            try {
                if (telecomManager != null && telecomManager.getPhoneAccount(phoneAccounts.get(i)) != null) {
                    label = telecomManager.getPhoneAccount(phoneAccounts.get(i)).getLabel();
                }
            } catch (Exception ignored) {}
            labels[i] = label == null ? String.format(Locale.getDefault(), "SIM %d", i + 1) : label.toString();
        }

        new AlertDialog.Builder(this)
                .setTitle("انتخاب سیم‌کارت")
                .setItems(labels, (dialog, which) -> {
                    prefs.edit().putInt(PREF_SELECTED_SIM_INDEX, which).apply();
                    selectedPhoneAccount = phoneAccounts.get(which);
                    placeCall(number, selectedPhoneAccount);
                })
                .show();
    }

    private void placeCall(String rawNumber, PhoneAccountHandle accountHandle) {
        String phone = normalizeVisiblePhone(rawNumber);
        Uri uri = Uri.parse("tel:" + Uri.encode(phone));
        try {
            TelecomManager telecomManager = (TelecomManager) getSystemService(TELECOM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && telecomManager != null) {
                Bundle extras = new Bundle();
                if (accountHandle != null) {
                    extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accountHandle);
                }
                telecomManager.placeCall(uri, extras);
            } else {
                Intent intent = new Intent(Intent.ACTION_CALL, uri);
                startActivity(intent);
            }
        } catch (SecurityException securityException) {
            Toast.makeText(this, "دسترسی تماس فعال نیست", Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Intent fallback = new Intent(Intent.ACTION_DIAL, uri);
            startActivity(fallback);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CALL_PHONE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                beginCallFlow();
            } else {
                Toast.makeText(this, "برای تماس مستقیم باید دسترسی تماس فعال باشد", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_READ_CONTACTS) {
            updateUi();
        }
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String normalizePhone(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    private String normalizeVisiblePhone(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c) || c == '+' || c == '*' || c == '#') sb.append(c);
        }
        return sb.toString();
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String trimmed = name.trim();
        return String.valueOf(trimmed.charAt(0)).toUpperCase(Locale.getDefault());
    }

    private String nonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static class ContactSuggestion {
        final String name;
        final String number;
        final String photoUri;

        ContactSuggestion(String name, String number, String photoUri) {
            this.name = name;
            this.number = number;
            this.photoUri = photoUri;
        }
    }

    private static class SpaceView extends View {
        public SpaceView(Context context) { super(context); }
    }
}
