# Upstream review
Pinned reference: https://github.com/ansaribilal14/jarvis/tree/f49d72d14773795901c92180841d1dab7c2c179e
Reviewed: LICENSE (MIT at default branch), settings.gradle.kts, app/build.gradle.kts, docs/AGENT_ENGINE.md and ConfirmationManager.kt (pinned revision for latter two).
Candidate design: bounded observe/decide/act/verify loop, grammar-constrained output, expiring per-action confirmations. No native Android code imported yet.
Other candidates (README-level review only): tokenarc/open-jarvis, judejulius/jarvis, atuldhull/jarvis, suyogpawate-lab/jarvis, naveen-anon/JarvisAI, AnaaySampat/jarvis, harsh-life/JARVIS, EduardoQuitto/jarvis, ulrichando/jarvis, theinizializer/Jarvis, Slagathore/jarvis, surajbannerjee/Jarvis, Mortalbeats/J.A.R.V.I.S.
Before importing: verify exact revision license, dependency provenance, Android build and device security.
