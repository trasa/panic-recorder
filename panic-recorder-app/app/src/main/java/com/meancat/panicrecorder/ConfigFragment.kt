package com.meancat.panicrecorder

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.core.content.edit

class ConfigFragment : Fragment() {

    private lateinit var apiUrlField: EditText
    private lateinit var secretField: EditText
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_config, container, false)

        apiUrlField = view.findViewById(R.id.edit_api_url)
        secretField = view.findViewById(R.id.edit_app_secret)
        saveButton = view.findViewById(R.id.button_save)
        cancelButton = view.findViewById(R.id.button_cancel)

        val prefs = requireContext().getSharedPreferences("panic_config", Context.MODE_PRIVATE)
        apiUrlField.setText(prefs.getString("api_url", ""))
        secretField.setText(prefs.getString("app_secret", ""))

        saveButton.setOnClickListener {
            val url = apiUrlField.text.toString().trim()
            val secret = secretField.text.toString().trim()

            if (url.isEmpty() || secret.isEmpty()) {
                Toast.makeText(requireContext(), "Both fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit() {
                putString("api_url", url)
                    .putString("app_secret", secret)
            }

            Toast.makeText(requireContext(), "Configuration saved", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }
        
        cancelButton.setOnClickListener { 
            Toast.makeText(requireContext(), "Changes discarded", Toast.LENGTH_SHORT).show()
            requireActivity().finish()
        }

        return view
    }
}