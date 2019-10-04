package com.brittlepins.brittleeye

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_about.*
import kotlinx.android.synthetic.main.content_about.*

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        linkedinButton.setOnClickListener { openLink(R.string.linkedin_link) }
        githubButton.setOnClickListener { openLink(R.string.github_link) }
        resSourcesTextView.setOnClickListener { openLink(R.string.freepik_link) }
        emailFAB.setOnClickListener { sendEmail() }
    }

    private fun openLink(urlId: Int) {
        val url = Uri.parse(getString(urlId))
        val intent = Intent(Intent.ACTION_VIEW, url)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun sendEmail() {
        val authorEmail = arrayOf(getString(R.string.admin_brittle_pins_email))
        val subject = getString(R.string.email_subject)
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, authorEmail)
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }
}
