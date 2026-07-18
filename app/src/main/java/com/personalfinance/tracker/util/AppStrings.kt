package com.personalfinance.tracker.util

import java.util.Locale

/**
 * Persian (Farsi) is the app language. All user-facing strings live here so the
 * whole UI stays consistent and translatable.
 */
object AppStrings {
    val appName = "مال‌دار"
    val moneyUnit = "تومان"

    // Bottom navigation
    val navHome = "خانه"
    val navAdd = "ثبت"
    val navLoans = "وام‌ها"
    val navReports = "گزارش‌ها"
    val navAccounts = "حساب‌ها"

    // Dashboard
    val overview = "نمای کلی"
    val onboardingTitle = "به مال‌دار خوش آمدید"
    val onboardingBody = "برای شروع: یک حساب بانکی اضافه کنید (تب حساب‌ها)، تراکنش‌هایتان را ثبت کنید (تب ثبت)، یا وام‌ها را مدیریت کنید."
    val monthlyTrend = "نمودار ماهانه"
    val noTrendData = "داده‌ای برای نمودار وجود ندارد."
    val totalBalance = "موجودی کل"
    val monthIncome = "درآمد این ماه"
    val monthExpense = "هزینه این ماه"
    val loanPaidThisMonth = "پرداخت وام این ماه"
    val recentTransactions = "تراکنش‌های اخیر"
    val noTransactions = "هنوز تراکنشی ثبت نشده است. از تب ثبت یکی اضافه کنید."
    val pendingSms = "%d تراکنش از پیامک نیاز به تایید دارد"
    val unreadSms = "پیامک‌های خوانده‌نشده"
    val reviewedSms = "پیامک‌های بررسی‌شده (%d)"
    val expand = "باز کردن"
    val collapse = "بستن"
    val allTransactionsSum = "جمع تراکنش‌ها"
    val editTransaction = "ویرایش تراکنش"
    val transactionCount = "%d تراکنش"

    // Add transaction
    val addTransaction = "ثبت تراکنش"
    val expense = "هزینه"
    val income = "درآمد"
    val amountLabel = "مبلغ (تومان)"
    val unit = "واحد:"
    val toman = "تومان"
    val rial = "ریال"
    val bankAccount = "حساب بانکی"
    val noneCash = "بدون حساب (نقدی)"
    val noteOptional = "یادداشت (اختیاری)"
    val relatedToLoan = "مرتبط با وام"
    val none = "هیچ‌کدام"
    val save = "ذخیره"
    val saved = "ذخیره شد!"
    val invalidAmount = "مبلغ معتبر وارد کنید"

    // Categories
    val category = "دسته‌بندی"
    val manageCategories = "مدیریت دسته‌ها…"
    val manageExpenseCategories = "مدیریت دسته‌های هزینه"
    val manageIncomeCategories = "مدیریت دسته‌های درآمد"
    val newCategory = "دسته جدید"
    val add = "افزودن"
    val done = "انجام شد"
    val save2 = "ذخیره"
    val categoryDeleted = "دسته حذف شد."
    val categoryDeletedReassigned = "%d تراکنش به «سایر» منتقل شد."

    // Bank accounts
    val bankAccounts = "حساب‌های بانکی"
    val noAccounts = "هنوز حسابی نیست. روی + بزنید تا یکی اضافه کنید."
    val addAccount = "افزودن حساب بانکی"
    val bankName = "نام بانک (مثل ملی)"
    val label = "برچسب (مثل حساب جاری)"
    val last4 = "۴ رقم آخر"
    val openingBalance = "موجودی اولیه (تومان)"
    val edit = "ویرایش"
    val refresh = "به‌روزرسانی از پیامک"
    val refreshDone = "موجودی از آخرین پیامک به‌روز شد"
    val refreshFailed = "پیامک معتبری برای این حساب یافت نشد"
    val smsSenders = "فرستندگان پیامک"
    val smsSendersHint = "شماره‌ها/شناسه‌هایی که برای پیامک تراکنش بررسی می‌شوند"
    val addSender = "افزودن فرستنده پیامک"
    val addAccountFirst = "اول یک حساب بانکی اضافه کنید."
    val detectedSenders = "فرستندگان شناسایی‌شده از پیامک‌ها"
    val senderId = "شناسه فرستنده"
    val linkToAccount = "اتصال به حساب"
    val senderHint = "شناسه فرستنده را دقیقاً همان‌طور که در پیام‌رسان نمایش داده می‌شود وارد کنید (مثل BPIR، ۳۰۰۰۷۳۲۷۳)."

    // Loans
    val loans = "وام‌ها"
    val loansSummaryTotal = "مجموع وام‌ها"
    val loansSummaryDue = "سررسید تا امروز این ماه"
    val loansSummaryRemain = "مانده ماه"
    val loansSummaryMonths = "تعداد ماه باقی‌مانده"
    val monthsFormat = "%d ماه"
    val loanInstallment = "قسط ماهانه"
    val loanTotalMonths = "تعداد اقساط"
    val loanMonthsLeft = "ماه باقی‌مانده"
    val loanProjection = "نمودار پیش‌بینی باقی‌مانده"
    val loanPayDay = "روز پرداخت ماهانه"
    val loansHint = "پیش از هر سررسید به شما یادآوری می‌شود."
    val noLoans = "هنوز وامی ثبت نشده است."
    val paid = "پرداخت شد"
    val due = "سررسید"
    val daysLeft = "روز مانده"
    val overdue = "سررسید گذشته"
    val amount = "مبلغ"
    val markPaid = "پرداخت شد"
    val payLoan = "پرداخت قسط"
    val loanLastPayment = "آخرین پرداخت"
    val loanNoPayment = "پرداختی ثبت نشده است"
    val loanPaymentAmount = "مبلغ پرداخت (تومان)"
    val delete = "حذف"
    val addLoan = "افزودن وام"
    val loanName = "نام وام"
    val principal = "مبلغ (تومان)"
    val dueInDays = "سررسید در چند روز"
    val payDayOfMonth = "روز پرداخت هر ماه"
    val remindDaysBefore = "یادآوری X روز قبل"
    val notesOptional = "یادداشت (اختیاری)"
    val cancel = "انصراف"
    val requiredField = "این فیلد الزامی است"
    val optional = "اختیاری"

    // Reports
    val reports = "گزارش‌ها"
    val prev = "قبلی"
    val next = "بعدی"
    val reportIncome = "درآمد"
    val reportExpense = "هزینه"
    val net = "خالص"
    val balanceTrend = "روند موجودی"
    val spendingByCategory = "هزینه بر اساس دسته‌بندی"
    val noExpenses = "امسال هزینه‌ای ثبت نشده است."

    // SMS confirmation
    val confirmSms = "تایید تراکنش‌های پیامکی"
    val confirmSmsHint = "از پیامک بانکی شما استخراج شد. پیش از ذخیره بررسی و تایید کنید."
    val nothingPending = "در حال حاضر چیزی در انتظار نیست."
    val from = "از:"
    val amountUnclear = "مبلغ نامشخص"
    val unknown = "نامشخص"
    val confirmEdit = "تایید / ویرایش"
    val ignore = "نادیده گرفتن"
    val confirmTransaction = "تایید تراکنش"

    // Crash log / diagnostics
    val crashLog = "گزارش خطاها"
    val noCrashLog = "هنوز خطایی ثبت نشده است."
    val sendLog = "ارسال گزارش"
    val clearLog = "پاک کردن گزارش"
    val close = "بستن"

    // Export / backup
    val exportData = "خروجی و پشتیبان‌گیری"
    val about = "درباره من"
    val versionLabel = "نسخهٔ برنامه: %s"
    val exportHint = "داده‌های خود را به صورت فایل ذخیره یا ارسال کنید."
    val exportCsv = "خروجی CSV"
    val exportJson = "خروجی JSON"
    val sendExport = "اشتراک‌گذاری فایل پشتیبان"

    // Updates
    val menuUpdates = "بررسی به‌روزرسانی"
    val updateAvailable = "نسخه جدید موجود است"
    val updateAvailableBody = "نسخه %s منتشر شده است. برای دریافت روی دکمه زیر بزنید."
    val download = "دانلود نسخه جدید"
    val upToDate = "برنامه به‌روز است"
    val upToDateBody = "شما آخرین نسخه (%s) را دارید."
    val checkFailed = "بررسی به‌روزرسانی ممکن نشد"
    val later = "بعداً"
    val skipVersion = "رد کردن این نسخه"
}

/**
 * Money is stored and displayed in Toman. Bank SMS arrive in Rial, so the parser
 * converts them (Rial / 10 = Toman). Formatting uses the Persian locale so
 * numbers render with Persian digits.
 */
object Money {
    val faLocale = Locale("fa", "IR")

    fun format(amount: Double): String {
        return "%,.0f".format(faLocale, amount)
    }

    fun format2(amount: Double): String {
        return "%,.2f".format(faLocale, amount)
    }
}
