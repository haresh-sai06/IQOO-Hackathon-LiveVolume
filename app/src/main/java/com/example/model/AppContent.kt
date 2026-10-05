package com.example.model

/**
 * Static, non-personal app content: brand imagery and editorial copy (FAQ,
 * guides, privacy policy).
 *
 * This file intentionally contains NO user data. Contacts, call records and the
 * signed-in profile are all loaded at runtime from Firebase Auth + Cloud
 * Firestore (see [com.example.data.repository.AuthRepository],
 * [com.example.data.repository.RealtimeContactsRepository] and
 * [com.example.data.local.CallHistoryRepository]).
 */
object AppContent {

  const val LOGO_URL =
    "https://lh3.googleusercontent.com/aida/AEtjO1V3pjsYMm4MENBJOi2bYi_-O8P4-jOfbixaSe_Il9muNjjOTgr6nJEvv5F7mu9S5UMhbO28Mb9f554EHINa13KQHluUuawZwiTsL-f7EgTdMZRwJDUdeKYIzabzJ9WrMy7DaRm7WhOCzUYWs5YKMpQULhJpC8PYhJjl4IimkRfVsTbiu3Y7V1974yj0Ht9HoRzOsbK-HMuwtocnsyH4PzQLk9Bj8LErZd-aYQ7oKpYvoKhUrZ5uJTxrFw"

  const val SUPPORT_HERO_URL =
    "https://lh3.googleusercontent.com/aida-public/AB6AXuBzmjwmMenrorKbbHjKLzPUwwoxd4ILS0AGneiuI1IzxRXsyVJyx3ZOmoqqcMFRrEgY5GmoGVYInviAZ2tvmts2OVhLHZe00uAsbQUcheb2AAr73tl4210uEPpuzJY1x0wXGHOPhclc6-G4-ehksND4_tpgBWbJ2ka6DN5W8yvszAO1YRuerSoM3Fbp2QT7ydbnh-ppnVLd-SweXSnkeT-GRI0Wfu8DMrPmdNbYAbJs-zHh-SkculXF"

  const val LIGHTING_GUIDE_URL =
    "https://lh3.googleusercontent.com/aida-public/AB6AXuDphO7c-ehF3nFwdUlVay2naDhgCRkNCtA0MnOnqhgWrmCTdPKY-iNXSZFbev4Pa8Ps0upHRc2PX5bzUb4egMxoerYl0moUdYmkEFB1SvsyZoxcaKEErt5ETG6xPqQugcBrS7WQ9b12JeY1otDVx2Tm2P4Z0SyQNb-KGEbnZHbHFK-POEWrANbBLRTCSQQlZ9qCSfApQGGEuLZdOGuqCwpeVqIOKMcF1LAgNNQsj8qsL41fAqwktE1Z"

  val faqs = listOf(
    FaqItem(
      id = "faq_1",
      question = "How do LiveVolume 3D calls work?",
      answer = "LiveVolume uses your phone's standard front camera to create an interactive 3D spatial feed. You can tilt your phone to naturally look around the caller. No headset, special sensors, or extra hardware are needed."
    ),
    FaqItem(
      id = "faq_2",
      question = "What devices are supported?",
      answer = "Any modern smartphone running iOS 15+ or Android 11+ with a standard front-facing camera is supported. The depth model runs directly on your device processor."
    ),
    FaqItem(
      id = "faq_3",
      question = "Why is my call dropping to standard 2D video?",
      answer = "When either caller experiences low bandwidth, unstable cellular signal, or very dim lighting, LiveVolume smoothly transitions to standard 2D video so your conversation stays clear and uninterrupted. It restores 3D spatial view automatically once conditions improve."
    ),
    FaqItem(
      id = "faq_4",
      question = "Is my 3D spatial data private?",
      answer = "Depth estimation runs on-device. Raw depth maps are not uploaded for reconstruction. Media and point-cloud packets use Agora transport encryption — not a custom end-to-end scheme where only you hold decryption keys."
    ),
    FaqItem(
      id = "faq_5",
      question = "How do I invite a friend to a 3D call?",
      answer = "Add each other as contacts using matching display names, or use Direct Call / Room Code so both phones join the same Agora channel. Share Call Link is not implemented yet."
    )
  )

  val guides = listOf(
    GuideItem(
      id = "g_1",
      title = "How to orbit & pinch during calls",
      summary = "Drag to angle viewpoints and pinch to scale.",
      category = "Camera & Lighting",
      iconName = "pan_tool"
    ),
    GuideItem(
      id = "g_2",
      title = "Optimizing Spatial Audio with Headphones",
      summary = "Hear callers speak from their actual position.",
      category = "Spatial Audio",
      iconName = "headphones"
    ),
    GuideItem(
      id = "g_3",
      title = "Ideal Lighting for High-Fidelity Meshing",
      summary = "Avoid backlighting for clean depth silhouettes.",
      category = "Camera & Lighting",
      iconName = "light_mode"
    ),
    GuideItem(
      id = "g_4",
      title = "Data Saver Mode for Cellular Connections",
      summary = "Point cloud compression consumes less data.",
      category = "Battery & Data",
      iconName = "signal_cellular_alt"
    )
  )

  val privacySections = listOf(
    PrivacySection(
      number = 1,
      title = "Information We Collect",
      content = "We collect standard account identifiers (such as your verified phone number or email address) strictly to authenticate you and enable directory lookups between saved contacts.\n\nStrict Policy: We never collect, inspect, store, or train AI models on your volumetric 3D point cloud streams or spatial audio feeds."
    ),
    PrivacySection(
      number = 2,
      title = "Device Permissions Usage",
      content = "â€¢ Camera: Required only during active live calls for real-time monocular depth estimation.\nâ€¢ Microphone: Required for live call audio (spatial/binaural processing is experimental).\nâ€¢ On-device ML + Agora RTC: Depth runs locally; media uses Agora transport encryption."
    ),
    PrivacySection(
      number = 3,
      title = "Peer-to-Peer Data Transfer",
      content = "Calls use Agora RTC for audio, video, and volumetric data. Agora may relay traffic through its network; treat privacy accordingly and do not claim custom end-to-end encryption."
    ),
    PrivacySection(
      number = 4,
      title = "Your Rights & Data Deletion",
      content = "You retain total ownership over your information. You can wipe your account metadata, call logs, and synchronized contacts anytime in Settings > Account > Delete Account."
    )
  )
}
