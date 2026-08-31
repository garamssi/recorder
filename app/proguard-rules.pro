# 프로젝트 전용 R8 규칙 (release 빌드).

# release 빌드에서 로그 호출 제거 (CLAUDE.md 7절). R8 optimize 패스가 부작용 없는 호출로 간주해 제거한다.
# 진단이 필요 없는 잡음만 걷어낸다. w/e 는 남긴다 — 녹화물을 지우는 코드(버려진 발행 정리,
# 발행 실패)의 유일한 진단 수단이고, 걷어내면 그것을 감싼 catch 가 CLAUDE.md 4절이 금지한
# 빈 catch 가 된다 (기능명세서 6.1절 [결정]).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
