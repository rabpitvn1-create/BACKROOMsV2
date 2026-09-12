package com.rabpit.backroom.core

import java.io.File

internal fun materializedCampaignHtml(): String {
  val relative = "build/generated/campaign-validation/src/main/assets/index.html"
  val roots = generateSequence(File(".").canonicalFile) { it.parentFile }.take(8).toList()
  val candidates = roots.flatMap { root ->
    listOf(
      File(root, relative),
      File(root, "app/$relative"),
      File(root, "android-apk/app/$relative")
    )
  }.distinctBy { it.absolutePath }
  val file = candidates.firstOrNull { it.isFile }
    ?: error("materialized campaign fixture not found; searched: " + candidates.joinToString { it.absolutePath })
  return file.readText(Charsets.UTF_8)
}
