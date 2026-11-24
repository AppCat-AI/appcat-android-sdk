-keep class com.appcat.sdk.** { *; }
-keep class com.appcat.core.** { *; }

# The SDK reads the Android advertising ID through reflection, and only when the
# app bundles com.google.android.gms:play-services-ads-identifier. Keep the
# reflected class and members so R8 neither renames nor strips them. The
# -dontwarn keeps apps that do not bundle the library building.
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient {
    public static com.google.android.gms.ads.identifier.AdvertisingIdClient$Info getAdvertisingIdInfo(android.content.Context);
}
-keep class com.google.android.gms.ads.identifier.AdvertisingIdClient$Info {
    public java.lang.String getId();
    public boolean isLimitAdTrackingEnabled();
}
-dontwarn com.google.android.gms.ads.identifier.**
