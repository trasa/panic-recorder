package com.meancat.panicrecorder

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.content.edit
import java.net.HttpURLConnection
import java.net.URL

class ConfigFragment : Fragment() {

    private lateinit var apiUrlField: EditText
    private lateinit var secretField: EditText
    private lateinit var s3PathPrefixField: EditText
    private lateinit var uploadCheckBox: CheckBox
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    
    private fun getS3PathPrefix(): String {
        return s3PathPrefixField.text.toString().trim().removePrefix("/").removeSuffix("/")
    }
    
    private fun writePreferences(prefs: SharedPreferences) {
        prefs.edit {
            putString("api_url", apiUrlField.text.toString().trim())
            putString("app_secret", secretField.text.toString().trim())
            putString("s3_path_prefix", getS3PathPrefix())
            putBoolean("enable_upload", uploadCheckBox.isChecked)
        } 
    }
    
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_config, container, false)

        apiUrlField = view.findViewById(R.id.edit_api_url)
        secretField = view.findViewById(R.id.edit_app_secret)
        s3PathPrefixField = view.findViewById(R.id.edit_s3_path_prefix)
        uploadCheckBox = view.findViewById(R.id.checkbox_enable_upload)
        saveButton = view.findViewById(R.id.button_save)
        cancelButton = view.findViewById(R.id.button_cancel)

        // where files are written to locally
        val storageDir = requireContext().getExternalFilesDir(null)
        val path = storageDir?.absolutePath ?: "Unavailable"
        val pathView = view.findViewById<TextView>(R.id.local_storage_path)
        pathView.text = path
        // make it clickable
        pathView.setOnClickListener { 
            storageDir?.let {
                try {
                    val uri = Uri.fromFile(it)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                } catch(e: Exception) {
                    Toast.makeText(requireContext(), "No file manager app found", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
         val prefs = requireContext().getSharedPreferences("panic_config", Context.MODE_PRIVATE)
        apiUrlField.setText(prefs.getString("api_url", ""))
        secretField.setText(prefs.getString("app_secret", ""))
        s3PathPrefixField.setText(prefs.getString("s3_path_prefix", "streams"))
        uploadCheckBox.isChecked = prefs.getBoolean("enable_upload", false)

        saveButton.setOnClickListener {
            val url = apiUrlField.text.toString().trim()
            val secret = secretField.text.toString().trim()
            val enableUpload = uploadCheckBox.isChecked

            if (!enableUpload) {
                // skip verification, save and exit
                writePreferences(prefs)
                Toast.makeText(requireContext(), "Configuration saved", Toast.LENGTH_SHORT).show()
                requireActivity().finish()
            } else {
                if (url.isEmpty() || secret.isEmpty()) {
                    Toast.makeText(requireContext(), "Both fields are required", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }

                // otherwise, verify the token
                Thread {
                    try {
                        val tokenUrl = "$url/api/auth/token"
                        val connection = URL(tokenUrl).openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Authorization", "Bearer $secret")
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.doOutput = true
                        connection.outputStream.write("{}".toByteArray())
                        val responseCode = connection.responseCode
                        if (responseCode in 200..299) {
                            requireActivity().runOnUiThread {
                                // success!
                                writePreferences(prefs)
                                Toast.makeText(
                                    requireContext(),
                                    "Configuration saved",
                                    Toast.LENGTH_SHORT
                                ).show()
                                requireActivity().finish()
                            }
                        } else {
                            requireActivity().runOnUiThread {
                                Toast.makeText(
                                    requireContext(),
                                    "Invalid app secret or API URL!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } catch (e: Exception) {
                        requireActivity().runOnUiThread {
                            Toast.makeText(
                                requireContext(),
                                "Error contacting server: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }.start()
            }
        }
        
        cancelButton.setOnClickListener { 
            Toast.makeText(requireContext(), "Changes discarded", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }

        return view
    }
}