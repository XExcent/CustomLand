package com.mukapp.customland

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mukapp.customland.databinding.FragmentScreenshotBinding
import java.io.File

class ScreenshotFragment : Fragment() {
    private var _binding: FragmentScreenshotBinding? = null
    private val binding
        get() = _binding!!

    private var screenshotPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screenshotPath = arguments?.getString(ARG_SCREENSHOT_PATH)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScreenshotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (screenshotPath != null) {
            val file = File(requireContext().filesDir, screenshotPath!!)
            if (file.exists()) {
                binding.ivScreenshot.setImageURI(android.net.Uri.fromFile(file))
                binding.ivScreenshot.visibility = View.VISIBLE
                binding.tvNoScreenshot.visibility = View.GONE
            } else {
                binding.ivScreenshot.visibility = View.GONE
                binding.tvNoScreenshot.visibility = View.VISIBLE
            }
        } else {
            binding.ivScreenshot.visibility = View.GONE
            binding.tvNoScreenshot.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SCREENSHOT_PATH = "screenshot_path"

        fun newInstance(screenshotPath: String?) =
            ScreenshotFragment().apply {
                arguments = Bundle().apply { putString(ARG_SCREENSHOT_PATH, screenshotPath) }
            }
    }
}
