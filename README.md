# IOS Call Dialer MVP

یک پروژه اندروید MVP برای اپلیکیشن تماس با طراحی مینیمال شبیه iOS.

## قابلیت‌های پیاده‌سازی‌شده

- صفحه Keypad با دکمه‌های گرد و طراحی خلوت
- شماره‌گیری مستقیم
- انتخاب سیم‌کارت در دستگاه‌های چندسیم‌کارته، در حد APIهای رسمی اندروید
- پیشنهاد مخاطبین هنگام وارد کردن بخشی از شماره
- Bottom Navigation نمایشی شبیه رفرنس UI
- مدیریت Permissionهای `CALL_PHONE`، `READ_CONTACTS` و `READ_PHONE_STATE`
- ذخیره انتخاب آخرین سیم‌کارت به صورت محلی

## روش ساخت APK در Android Studio

1. پروژه را در Android Studio باز کنید.
2. صبر کنید Gradle Sync کامل شود.
3. از منوی بالا مسیر زیر را بزنید:
   `Build > Build Bundle(s) / APK(s) > Build APK(s)`
4. فایل خروجی معمولاً اینجاست:
   `app/build/outputs/apk/debug/app-debug.apk`

## نکته مهم

این نسخه MVP است و هنوز Default Dialer کامل، Caller ID، Spam Detection و صفحه تماس ورودی اختصاصی ندارد.
برای آن قابلیت‌ها باید فاز پیشرفته با `RoleManager`، `InCallService` و `CallScreeningService` طراحی شود.
