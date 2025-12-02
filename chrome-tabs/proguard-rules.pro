-keep public class de.mrapp.android.tabswitcher.TabSwitcher {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
-keep class de.mrapp.android.tabswitcher.** { *; }

-keep class de.mrapp.android.tabswitcher.* {
    *;
}

-keepclassmembers class **.R$* {
    public static int *;
}
