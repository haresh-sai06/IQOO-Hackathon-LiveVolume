package com.example.model

object DataRepository {

  const val LOGO_URL =
    "https://lh3.googleusercontent.com/aida/AEtjO1V3pjsYMm4MENBJOi2bYi_-O8P4-jOfbixaSe_Il9muNjjOTgr6nJEvv5F7mu9S5UMhbO28Mb9f554EHINa13KQHluUuawZwiTsL-f7EgTdMZRwJDUdeKYIzabzJ9WrMy7DaRm7WhOCzUYWs5YKMpQULhJpC8PYhJjl4IimkRfVsTbiu3Y7V1974yj0Ht9HoRzOsbK-HMuwtocnsyH4PzQLk9Bj8LErZd-aYQ7oKpYvoKhUrZ5uJTxrFw"

  const val SARAH_VIDEO_FEED_URL =
    "https://lh3.googleusercontent.com/aida-public/AB6AXuD-8R1GNAD68FAMvG67ruclrw8JxXrUVYtbcGwllwytX-x_WM6FoQFI8SCzZyYKoWhggV9TB9IDP3pKvwxqAfkqQpq9NcM0-uhT-5sVeMRHM6t5K8D3WRWaofaBiT10KwYobkbjntw2eC30m3DhnVUIRIB56xVIXvlJDtahrk2V5y6o-tma90qVZrfA7JYKflU5XdrFRpWf6ITQiq3gl2GUtWCDZsVFlOAXXl8WdYj7qe0l7fL4HdVk"

  const val SARAH_AVATAR_URL =
    "https://lh3.googleusercontent.com/aida-public/AB6AXuDY-kuwHnfBauA9LWiDld3tkQs-sYEcWZamwDabaPwKo-DWinE-1CYLeVi3X4bZeVJ0JTt3hkq1ls1vaz3FyCE9qXtp12jmYdDynQpQsaBSdVo9M8Ja7XiI0cyYYMBXtrXer7Ljyqpvpj4vDGvFy-bUutzLW9ieUNcA9Yzc3H1HTc8sn_jzi834G0G4DeLkQZOlzsf-IH1k82egIvlGyC5A35vKGvQPEYvvurKOVZWojYXdfx9LLt8j"

  const val SELF_PIP_URL =
    "https://lh3.googleusercontent.com/aida-public/AB6AXuCEV7HVgmECvp-63EQIIoF-0ojFIWp5jBjW-I1FYHQZm73AxRH9iYoUFDcTPyGtWAUUUrdaRjcb3d6opaVBWZHqUme-7FD6unWaaUh7xQWJU2VXrAFQxBMaVyHJW5svVgnJZFuaWMpwu5A_ruCEbWzAdTcHOgPYID_wvD5yz02Tre2bwHiy3lz4hpVsLOjftal54MgGCwjmecJRFSoOHVZpgXMC6QJr_O8ENpNbmgaF-rhrEem3NbZ6"

  const val SUPPORT_HERO_URL =
    "https://lh3.googleusercontent.com/aida-public/AB6AXuBzmjwmMenrorKbbHjKLzPUwwoxd4ILS0AGneiuI1IzxRXsyVJyx3ZOmoqqcMFRrEgY5GmoGVYInviAZ2tvmts2OVhLHZe00uAsbQUcheb2AAr73tl4210uEPpuzJY1x0wXGHOPhclc6-G4-ehksND4_tpgBWbJ2ka6DN5W8yvszAO1YRuerSoM3Fbp2QT7ydbnh-ppnVLd-SweXSnkeT-GRI0Wfu8DMrPmdNbYAbJs-zHh-SkculXF"

  const val LIGHTING_GUIDE_URL =
    "https://lh3.googleusercontent.com/aida-public/AB6AXuDphO7c-ehF3nFwdUlVay2naDhgCRkNCtA0MnOnqhgWrmCTdPKY-iNXSZFbev4Pa8Ps0upHRc2PX5bzUb4egMxoerYl0moUdYmkEFB1SvsyZoxcaKEErt5ETG6xPqQugcBrS7WQ9b12JeY1otDVx2Tm2P4Z0SyQNb-KGEbnZHbHFK-POEWrANbBLRTCSQQlZ9qCSfApQGGEuLZdOGuqCwpeVqIOKMcF1LAgNNQsj8qsL41fAqwktE1Z"

  val myProfile = Contact(
    id = "my_card",
    name = "Sarah Chen",
    initials = "SC",
    phone = "+1 (555) 349-8201",
    status = "3D Live Enabled",
    avatarUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDY-kuwHnfBauA9LWiDld3tkQs-sYEcWZamwDabaPwKo-DWinE-1CYLeVi3X4bZeVJ0JTt3hkq1ls1vaz3FyCE9qXtp12jmYdDynQpQsaBSdVo9M8Ja7XiI0cyYYMBXtrXer7Ljyqpvpj4vDGvFy-bUutzLW9ieUNcA9Yzc3H1HTc8sn_jzi834G0G4DeLkQZOlzsf-IH1k82egIvlGyC5A35vKGvQPEYvvurKOVZWojYXdfx9LLt8j",
    isSpatialReady = true,
    isOnline = true
  )

  val quickConnectFavorites = listOf(
    Contact(
      id = "c_alex",
      name = "Alex R.",
      initials = "AR",
      phone = "+1 (555) 892-1200",
      status = "Spatial Audio • Available",
      avatarUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDudrMDX5P7Xau2rYTJoWs_U3mfmIVFS6g0U-C2G4kulP8Fwo0qYIrvBIaL4ZgheFVTdPh4Wbf3iXQZH-2sjLftpYgQ7qsufkGdIDqSKHWBrmY4uTMkB2jSCCpxdAhG07bn1DOs4k9vtubWSW4bTxaXxc1BDEI4gwpfD42LXHJ89atSmKrTZFgu-OPoUDhO4uQTnKT-8n02aNpTKMoAX_vjtcMpTPg7fXf6qmdYJq25f3DiJ4Q-D7JS",
      isSpatialReady = true,
      isOnline = true,
      isFavorite = true,
      section = "A"
    ),
    Contact(
      id = "c_maya",
      name = "Maya Lin",
      initials = "ML",
      phone = "+1 (555) 301-4491",
      status = "Binaural Headset Connected",
      avatarUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDBqk9hrQK4gtxhtzkr-LilcT0qTDX8sNUiNaYYymwortROHxX_k2zNH3EvkPl_61an8F8pSYVh1Bhd805EH3X5pn2ZZxq321gKto412FbtOevkOw8VVT_sEutyjzVXGMV837zO0rwiZnCoSu8qISxtWToyI4OggwLlPtPB7UeycSYFJD9Dtj8BScLnSsSPBhx0w-dIjRbcBsSNQt_Tm8RLKZomRoEcpGfsCVwwMLtj4YZeaIS2RYl7",
      isSpatialReady = true,
      isOnline = true,
      isFavorite = true,
      section = "M"
    ),
    Contact(
      id = "c_david",
      name = "David M.",
      initials = "DM",
      phone = "+1 (415) 890-2134",
      status = "Home Office",
      avatarUrl = null,
      isSpatialReady = false,
      isOnline = false,
      isFavorite = true,
      section = "D"
    ),
    Contact(
      id = "c_elena",
      name = "Dr. Elena",
      initials = "ER",
      phone = "+1 (555) 723-9081",
      status = "LiveVolume Studio • Active",
      avatarUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCX8QszSfRCWndcih7gcLjN-_cAP7cItoDRamfF01usLs8aEsHj64atpmKZ8kWgUybf6f0KprDcL7xdhdpiEIaxCzGwxZ8wyp-MJlYD5CDqpZ8r1MsnQvi0Ii3OFt4wRBQAwy6voKoBZ2vk-KsK2iydKQc-3C_wEHnC3w2KAbcN4TMCe9xdDhAl_za0ARjwg8a9pNR5HOuzf6tLRqGck5pgKVRtHiLqcwtwOwGyTwjHczR0eh96B4mk",
      isSpatialReady = true,
      isOnline = true,
      isFavorite = true,
      section = "E"
    )
  )

  val allContacts = listOf(
    Contact(
      id = "c_alex_full",
      name = "Alex Rivera",
      initials = "AR",
      phone = "+1 (555) 892-1200",
      status = "Spatial Audio • Available",
      avatarUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBcApQNvvUXPWfxxC6QbKdnm2BoQ6H3aaDPliLINq9_6gU6N-4mxmwAGvoTHnqi-XoBfIEnncz6QTflUCGt1mORb6PzdzN5WlgCk_bT4lwVl46UbXwQqsWgL_-pMMmjElMoGhskPsy5KeUk2T_xYlCUfvk0Ye-ibTpnVcR82xD9mpwaDjH3n4XgEozysgUwKIx1nZseKtYAvuxNUqxrF7lI9m8shuXhElqgfhMTVGU8fnY1sPj3BmvL",
      isSpatialReady = true,
      isOnline = true,
      isFavorite = true,
      section = "A"
    ),
    Contact(
      id = "c_amara",
      name = "Amara Osei",
      initials = "AO",
      phone = "+44 7911 123456",
      status = "+44 7911 123456 • Mobile",
      avatarUrl = null,
      isSpatialReady = false,
      isOnline = false,
      isFavorite = false,
      section = "A"
    ),
    Contact(
      id = "c_david_full",
      name = "David Miller",
      initials = "DM",
      phone = "+1 (415) 890-2134",
      status = "+1 (415) 890-2134 • Home Office",
      avatarUrl = null,
      isSpatialReady = false,
      isOnline = false,
      isFavorite = true,
      section = "D"
    ),
    Contact(
      id = "c_elena_full",
      name = "Dr. Elena Rostova",
      initials = "ER",
      phone = "+1 (555) 723-9081",
      status = "LiveVolume Studio • Active",
      avatarUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuArdJFCrmn0ZgdYk-_KiuFJRv4pt0W6yBPb3H5aHow1Ipc8S0Tzl0CPQhFkV0Iv81yDY7v3fhC_SWjWayZgvByS5ULx87OyA_2KKKktTAJjwMcK-YCWVC6jgX2KkVh-BWEEk7wyPSOD0OR9G4BYBRLpGZ13f_8neopvIgq1Gu3E3wVegBdW1sNgTriAAMoNfI23CLMdQzj5hyHZC6IFN0X1coXHbC5N7ZfcBMnDgcVJmPaZmkYB-DgV",
      isSpatialReady = true,
      isOnline = true,
      isFavorite = true,
      section = "E"
    ),
    Contact(
      id = "c_marcus",
      name = "Marcus Vance",
      initials = "MV",
      phone = "+1 (206) 555-0199",
      status = "+1 (206) 555-0199 • Mobile",
      avatarUrl = null,
      isSpatialReady = false,
      isOnline = false,
      isFavorite = false,
      section = "M"
    ),
    Contact(
      id = "c_maya_full",
      name = "Maya Lin",
      initials = "ML",
      phone = "+1 (555) 301-4491",
      status = "Binaural Headset Connected",
      avatarUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCav6fsIh8Jg74vUi0cgtJ7sQBUJd0yV-Lo7O415oqZ_FjIQG2VIJOn_Segj5VWlR6L07POH8Vk6-JMz8RCIVF3F07XF5Iuk-P_k4kEIz2KHtfmDDx6KGUYSUjtjST0T3syCJZ_g88GpG4HOnBtE1rYJeSbLqTsdmhzuzUtS5UFGqlxFDBEpf9PZ7F0iFqEc1brBnDeIDY-Rz1jnS707PV_kkvQPbPxe28Mq93GZ57e2K2SFNOWZAi5",
      isSpatialReady = true,
      isOnline = true,
      isFavorite = true,
      section = "M"
    ),
    Contact(
      id = "c_samira",
      name = "Samira Khan",
      initials = "SK",
      phone = "+1 (650) 412-9901",
      status = "+1 (650) 412-9901 • LiveVolume",
      avatarUrl = null,
      isSpatialReady = true,
      isOnline = true,
      isFavorite = false,
      section = "S"
    ),
    Contact(
      id = "c_thomas",
      name = "Thomas Berg",
      initials = "TB",
      phone = "+49 30 901820",
      status = "+49 30 901820 • Mobile",
      avatarUrl = null,
      isSpatialReady = false,
      isOnline = false,
      isFavorite = false,
      section = "T"
    )
  )

  val callRecords = listOf(
    CallRecord(
      id = "call_1",
      contactName = "Sarah Chen",
      initials = "SC",
      avatarUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDY-kuwHnfBauA9LWiDld3tkQs-sYEcWZamwDabaPwKo-DWinE-1CYLeVi3X4bZeVJ0JTt3hkq1ls1vaz3FyCE9qXtp12jmYdDynQpQsaBSdVo9M8Ja7XiI0cyYYMBXtrXer7Ljyqpvpj4vDGvFy-bUutzLW9ieUNcA9Yzc3H1HTc8sn_jzi834G0G4DeLkQZOlzsf-IH1k82egIvlGyC5A35vKGvQPEYvvurKOVZWojYXdfx9LLt8j",
      callType = CallType.SPATIAL_3D,
      direction = CallDirection.INCOMING,
      duration = "18m 42s",
      timestamp = "2:15 PM",
      period = CallPeriod.TODAY,
      isOnline = true
    ),
    CallRecord(
      id = "call_2",
      contactName = "David Miller",
      initials = "DM",
      avatarUrl = null,
      callType = CallType.MISSED,
      direction = CallDirection.MISSED,
      duration = "Missed (2 rings)",
      timestamp = "11:04 AM",
      period = CallPeriod.TODAY,
      isOnline = false
    ),
    CallRecord(
      id = "call_3",
      contactName = "Maya Lin",
      initials = "ML",
      avatarUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDBqk9hrQK4gtxhtzkr-LilcT0qTDX8sNUiNaYYymwortROHxX_k2zNH3EvkPl_61an8F8pSYVh1Bhd805EH3X5pn2ZZxq321gKto412FbtOevkOw8VVT_sEutyjzVXGMV837zO0rwiZnCoSu8qISxtWToyI4OggwLlPtPB7UeycSYFJD9Dtj8BScLnSsSPBhx0w-dIjRbcBsSNQt_Tm8RLKZomRoEcpGfsCVwwMLtj4YZeaIS2RYl7",
      callType = CallType.VIDEO,
      direction = CallDirection.OUTGOING,
      duration = "6m 12s",
      timestamp = "9:30 AM",
      period = CallPeriod.TODAY,
      isOnline = true
    ),
    CallRecord(
      id = "call_4",
      contactName = "Alex Rivera",
      initials = "AR",
      avatarUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDudrMDX5P7Xau2rYTJoWs_U3mfmIVFS6g0U-C2G4kulP8Fwo0qYIrvBIaL4ZgheFVTdPh4Wbf3iXQZH-2sjLftpYgQ7qsufkGdIDqSKHWBrmY4uTMkB2jSCCpxdAhG07bn1DOs4k9vtubWSW4bTxaXxc1BDEI4gwpfD42LXHJ89atSmKrTZFgu-OPoUDhO4uQTnKT-8n02aNpTKMoAX_vjtcMpTPg7fXf6qmdYJq25f3DiJ4Q-D7JS",
      callType = CallType.SPATIAL_3D,
      direction = CallDirection.INCOMING,
      duration = "32m 10s",
      timestamp = "4:45 PM",
      period = CallPeriod.YESTERDAY,
      isOnline = true
    ),
    CallRecord(
      id = "call_5",
      contactName = "Dr. Elena Rostova",
      initials = "ER",
      avatarUrl = null,
      callType = CallType.AUDIO,
      direction = CallDirection.OUTGOING,
      duration = "12m 05s",
      timestamp = "1:20 PM",
      period = CallPeriod.YESTERDAY,
      isOnline = false
    ),
    CallRecord(
      id = "call_6",
      contactName = "Sarah Chen",
      initials = "SC",
      avatarUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDY-kuwHnfBauA9LWiDld3tkQs-sYEcWZamwDabaPwKo-DWinE-1CYLeVi3X4bZeVJ0JTt3hkq1ls1vaz3FyCE9qXtp12jmYdDynQpQsaBSdVo9M8Ja7XiI0cyYYMBXtrXer7Ljyqpvpj4vDGvFy-bUutzLW9ieUNcA9Yzc3H1HTc8sn_jzi834G0G4DeLkQZOlzsf-IH1k82egIvlGyC5A35vKGvQPEYvvurKOVZWojYXdfx9LLt8j",
      callType = CallType.SPATIAL_3D,
      direction = CallDirection.OUTGOING,
      duration = "45m 19s",
      timestamp = "Mon",
      period = CallPeriod.EARLIER_THIS_WEEK,
      isOnline = true
    ),
    CallRecord(
      id = "call_7",
      contactName = "David Miller",
      initials = "DM",
      avatarUrl = null,
      callType = CallType.MISSED,
      direction = CallDirection.MISSED,
      duration = "Missed (1 ring)",
      timestamp = "Mon",
      period = CallPeriod.EARLIER_THIS_WEEK,
      isOnline = false
    )
  )

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
      answer = "Yes, entirely. Depth calculations take place in real-time on your phone. No raw biometric depth maps or room scans are ever stored or transmitted to external servers. Your video call is end-to-end encrypted."
    ),
    FaqItem(
      id = "faq_5",
      question = "How do I invite a friend to a 3D call?",
      answer = "You can start a call directly from your Contacts tab, or tap \"Share Call Link\" inside any active session. Your friend can join in high quality with a single tap."
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
      content = "• Camera: Required only during active live calls for real-time monocular depth estimation.\n• Microphone: Required for live binaural spatial audio capture during calls.\n• Neural Engine / WebRTC: Used for on-device depth estimation and P2P connection handshake."
    ),
    PrivacySection(
      number = 3,
      title = "Peer-to-Peer Data Transfer",
      content = "Whenever network topology allows, calls connect directly peer-to-peer (P2P). If restrictive firewalls necessitate a TURN relay, the relay transmits only encrypted packets and cannot decrypt the spatial stream."
    ),
    PrivacySection(
      number = 4,
      title = "Your Rights & Data Deletion",
      content = "You retain total ownership over your information. You can wipe your account metadata, call logs, and synchronized contacts anytime in Settings > Account > Delete Account."
    )
  )
}
