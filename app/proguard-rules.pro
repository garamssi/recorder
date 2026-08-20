# 프로젝트 전용 R8 규칙 (release 빌드).

# release 빌드에서 로그 호출 제거 (CLAUDE.md 7절). R8 optimize 패스가 부작용 없는 호출로 간주해 제거한다.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
