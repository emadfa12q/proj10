# Feature Mapping v8

## Idle / away time

- `idle_limit_seconds` در جدول settings ذخیره می‌شود.
- مقدار پیش‌فرض ۱۰ دقیقه است.
- مقدار 0 یعنی خاموش.
- فاصله‌های بین فعالیت‌ها اگر بزرگ‌تر یا مساوی حد توقف باشند، به عنوان بیکاری حساب می‌شوند.
- اگر Screenshot Accessibility روشن باشد، آخرین تعامل کاربر ثبت می‌شود و سرویس می‌تواند فعالیت جاری را بعد از حد توقف ببندد.
- زمان بیکاری در صفحه خانه، گزارش‌ها و PDF نمایش داده می‌شود.

## Activity screen

- Inline screenshot preview removed for better speed.
- Screenshot opens only when the user taps the screenshot button.

## Settings

- PIN can be changed by the user.
- Screenshot retention can be selected: 3 days, 7 days, 30 days, or keep forever.
- Idle/away timeout can be changed by the user.

## Reports

- PDF export uses Android document picker so the user can choose file name and location.
- Active time and idle time are both included in the report summary.

## Timeline

- Zoom controls added: 24h, 12h, 6h, 3h.
