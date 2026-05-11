package com.reilandeubank.unprocess.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.reilandeubank.unprocess.R
import com.reilandeubank.unprocess.databinding.FragmentSettingsBinding
import com.reilandeubank.unprocess.engine.FilmPreset
import com.reilandeubank.unprocess.engine.FilmStyle
import com.reilandeubank.unprocess.engine.FilmrConfig
import com.reilandeubank.unprocess.engine.FilmrEngine
import com.reilandeubank.unprocess.engine.OutputMode
import com.reilandeubank.unprocess.engine.SimulationMode
import com.reilandeubank.unprocess.engine.WhiteBalanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Settings screen exposing all filmr engine parameters.
 * Changes are persisted immediately to SharedPreferences.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var config: FilmrConfig

    // Flag to suppress listener callbacks while we are restoring saved state
    private var restoring = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext()
            .getSharedPreferences(FilmrConfig.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        config = FilmrConfig.load(prefs)

        setupToolbar()
        setupPresetSpinner()
        setupStyleSpinner()
        setupSimulationModeSpinner()
        setupOutputModeSpinner()
        setupWhiteBalanceModeSpinner()
        setupSliders()
        setupSwitches()
        setupDepthModelSection()
        setupResetButton()

        restoreValues()
    }

    // ------------------------------------------------------------------
    // Toolbar / back
    // ------------------------------------------------------------------

    private fun setupToolbar() {
        binding.settingsToolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    // ------------------------------------------------------------------
    // Spinners
    // ------------------------------------------------------------------

    private fun setupPresetSpinner() {
        val entries = FilmPreset.entries.map { it.label }
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, entries)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerPreset.adapter = adapter
        binding.spinnerPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                if (restoring) return
                config = config.copy(preset = FilmPreset.entries[pos])
                saveConfig()
            }
            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    private fun setupStyleSpinner() {
        val entries = listOf("Accurate", "Artistic", "Vintage", "High Contrast", "Pastel")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, entries)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerStyle.adapter = adapter
        binding.spinnerStyle.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                if (restoring) return
                config = config.copy(filmStyle = FilmStyle.entries[pos])
                saveConfig()
            }
            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    private fun setupSimulationModeSpinner() {
        val entries = listOf("Accurate (Full Spectrum)", "Fast (Matrix)")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, entries)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerSimMode.adapter = adapter
        binding.spinnerSimMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                if (restoring) return
                config = config.copy(
                    simulationMode = if (pos == 0) SimulationMode.ACCURATE else SimulationMode.FAST
                )
                saveConfig()
            }
            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    private fun setupOutputModeSpinner() {
        val entries = listOf("Positive (Normal)", "Negative (Inverted)")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, entries)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerOutputMode.adapter = adapter
        binding.spinnerOutputMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                if (restoring) return
                config = config.copy(
                    outputMode = if (pos == 0) OutputMode.POSITIVE else OutputMode.NEGATIVE
                )
                saveConfig()
            }
            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    private fun setupWhiteBalanceModeSpinner() {
        val entries = listOf("Auto", "Gray World", "White Patch", "Off")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, entries)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerWbMode.adapter = adapter
        binding.spinnerWbMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
                if (restoring) return
                config = config.copy(
                    whiteBalanceMode = when (pos) {
                        1 -> WhiteBalanceMode.GRAY
                        2 -> WhiteBalanceMode.WHITE
                        3 -> WhiteBalanceMode.OFF
                        else -> WhiteBalanceMode.AUTO
                    }
                )
                saveConfig()
            }
            override fun onNothingSelected(parent: AdapterView<*>) = Unit
        }
    }

    // ------------------------------------------------------------------
    // Sliders (SeekBar)
    // ------------------------------------------------------------------

    private fun setupSliders() {
        // exposure_time  0.1 – 4.0  (seekbar 0-390, +10 → /100)
        setupSeekBar(binding.seekExposure, binding.labelExposure, "Exposure Time", 0, 390) { raw ->
            val v = (raw + 10) / 100f
            config = config.copy(exposureTime = v)
            "%.2f s".format(v)
        }
        // white_balance_strength  0.0 – 1.0
        setupSeekBar(binding.seekWbStrength, binding.labelWbStrength, "WB Strength", 0, 100) { raw ->
            val v = raw / 100f
            config = config.copy(whiteBalanceStrength = v)
            "%.2f".format(v)
        }
        // warmth  -1.0 – 1.0
        setupSeekBar(binding.seekWarmth, binding.labelWarmth, "Warmth", 0, 200) { raw ->
            val v = (raw - 100) / 100f
            config = config.copy(warmth = v)
            "%.2f".format(v)
        }
        // saturation  0.0 – 2.0
        setupSeekBar(binding.seekSaturation, binding.labelSaturation, "Saturation", 0, 200) { raw ->
            val v = raw / 100f
            config = config.copy(saturation = v)
            "%.2f".format(v)
        }
        // object_motion_amount  0.0 – 1.0  (depth-based, needs depth model)
        setupSeekBar(binding.seekObjectMotion, binding.labelObjectMotion, "Object Motion (depth)", 0, 100) { raw ->
            val v = raw / 100f
            config = config.copy(objectMotionAmount = v)
            "%.2f".format(v)
        }
        // motion_blur_amount  0.0 – 2.0
        setupSeekBar(binding.seekMotionBlur, binding.labelMotionBlur, "Motion Blur", 0, 200) { raw ->
            val v = raw / 100f
            config = config.copy(motionBlurAmount = v)
            "%.2f".format(v)
        }
        // dof_amount  0.0 – 1.0
        setupSeekBar(binding.seekDofAmount, binding.labelDofAmount, "Depth of Field", 0, 100) { raw ->
            val v = raw / 100f
            config = config.copy(dofAmount = v)
            "%.2f".format(v)
        }
        // dof_focus  0.0 – 1.0
        setupSeekBar(binding.seekDofFocus, binding.labelDofFocus, "DOF Focus", 0, 100) { raw ->
            val v = raw / 100f
            config = config.copy(dofFocus = v)
            "%.2f".format(v)
        }
        // dof_swirl  0.0 – 1.0
        setupSeekBar(binding.seekDofSwirl, binding.labelDofSwirl, "DOF Swirl (Petzval)", 0, 100) { raw ->
            val v = raw / 100f
            config = config.copy(dofSwirl = v)
            "%.2f".format(v)
        }
        // rotational_blur_amount  0.0 – 2.0
        setupSeekBar(binding.seekRotBlur, binding.labelRotBlur, "Rotational Blur", 0, 200) { raw ->
            val v = raw / 100f
            config = config.copy(rotationalBlurAmount = v)
            "%.2f".format(v)
        }
    }

    private fun setupSeekBar(
        seekBar: SeekBar,
        label: TextView,
        name: String,
        min: Int,
        max: Int,
        onChanged: (Int) -> String
    ) {
        seekBar.max = max - min
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val valueLabel = onChanged(progress + min)
                label.text = "$name: $valueLabel"
                if (!restoring && fromUser) saveConfig()
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    // ------------------------------------------------------------------
    // Switches
    // ------------------------------------------------------------------

    private fun setupSwitches() {
        binding.switchGrain.setOnCheckedChangeListener { _, checked ->
            if (!restoring) { config = config.copy(enableGrain = checked); saveConfig() }
        }
        binding.switchAutoLevels.setOnCheckedChangeListener { _, checked ->
            if (!restoring) { config = config.copy(autoLevels = checked); saveConfig() }
        }
        binding.switchLightLeak.setOnCheckedChangeListener { _, checked ->
            if (!restoring) { config = config.copy(lightLeakEnabled = checked); saveConfig() }
        }
    }

    // ------------------------------------------------------------------
    // Depth model download
    // ------------------------------------------------------------------

    private fun depthModelFile(): File =
        File(requireContext().filesDir, "models/${FilmrEngine.DEPTH_MODEL_FILENAME}")

    private fun setupDepthModelSection() {
        refreshDepthModelStatus()
        binding.btnDownloadDepthModel.setOnClickListener {
            it.isEnabled = false
            binding.labelDepthModelStatus.text = "Downloading…"
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val dest = depthModelFile().also { f -> f.parentFile?.mkdirs() }
                    val tmp = File(dest.parent, dest.name + ".tmp")
                    URL(FilmrEngine.DEPTH_MODEL_URL).openStream().use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    tmp.renameTo(dest)
                    withContext(Dispatchers.Main) { refreshDepthModelStatus() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.labelDepthModelStatus.text = "Download failed: ${e.message}"
                        binding.btnDownloadDepthModel.isEnabled = true
                    }
                }
            }
        }
    }

    private fun refreshDepthModelStatus() {
        val f = depthModelFile()
        if (!FilmrEngine.isDepthEstimationSupported) {
            binding.labelDepthModelStatus.text =
                "Depth not compiled in — rebuild with --features depth"
            binding.btnDownloadDepthModel.isEnabled = false
        } else if (f.exists()) {
            val mb = f.length() / 1_048_576
            binding.labelDepthModelStatus.text = "Model ready (${mb} MB)"
            binding.btnDownloadDepthModel.text = "Re-download Model"
            binding.btnDownloadDepthModel.isEnabled = true
        } else {
            binding.labelDepthModelStatus.text = "Model: not downloaded (~95 MB)"
            binding.btnDownloadDepthModel.text = "Download Depth Model"
            binding.btnDownloadDepthModel.isEnabled = true
        }
    }

    // ------------------------------------------------------------------
    // Reset
    // ------------------------------------------------------------------

    private fun setupResetButton() {
        binding.btnResetDefaults.setOnClickListener {
            config = FilmrConfig()
            saveConfig()
            restoreValues()
        }
    }

    // ------------------------------------------------------------------
    // State restoration & persistence
    // ------------------------------------------------------------------

    private fun restoreValues() {
        restoring = true

        binding.spinnerPreset.setSelection(config.preset.ordinal)
        binding.spinnerStyle.setSelection(config.filmStyle.ordinal)
        binding.spinnerSimMode.setSelection(
            if (config.simulationMode == SimulationMode.ACCURATE) 0 else 1
        )
        binding.spinnerOutputMode.setSelection(
            if (config.outputMode == OutputMode.POSITIVE) 0 else 1
        )
        binding.spinnerWbMode.setSelection(
            when (config.whiteBalanceMode) {
                WhiteBalanceMode.AUTO -> 0
                WhiteBalanceMode.GRAY -> 1
                WhiteBalanceMode.WHITE -> 2
                WhiteBalanceMode.OFF -> 3
            }
        )

        // Sliders — convert float value → seekbar progress
        binding.seekExposure.progress = ((config.exposureTime * 100).toInt() - 10).coerceIn(0, 390)
        binding.seekWbStrength.progress = (config.whiteBalanceStrength * 100).toInt().coerceIn(0, 100)
        binding.seekWarmth.progress = ((config.warmth * 100) + 100).toInt().coerceIn(0, 200)
        binding.seekSaturation.progress = (config.saturation * 100).toInt().coerceIn(0, 200)
        binding.seekObjectMotion.progress = (config.objectMotionAmount * 100).toInt().coerceIn(0, 100)
        binding.seekMotionBlur.progress = (config.motionBlurAmount * 100).toInt().coerceIn(0, 200)
        binding.seekDofAmount.progress = (config.dofAmount * 100).toInt().coerceIn(0, 100)
        binding.seekDofFocus.progress = (config.dofFocus * 100).toInt().coerceIn(0, 100)
        binding.seekDofSwirl.progress = (config.dofSwirl * 100).toInt().coerceIn(0, 100)
        binding.seekRotBlur.progress = (config.rotationalBlurAmount * 100).toInt().coerceIn(0, 200)

        // Update labels
        binding.labelExposure.text = "Exposure Time: %.2f s".format(config.exposureTime)
        binding.labelWbStrength.text = "WB Strength: %.2f".format(config.whiteBalanceStrength)
        binding.labelWarmth.text = "Warmth: %.2f".format(config.warmth)
        binding.labelSaturation.text = "Saturation: %.2f".format(config.saturation)
        binding.labelObjectMotion.text = "Object Motion (depth): %.2f".format(config.objectMotionAmount)
        binding.labelMotionBlur.text = "Motion Blur: %.2f".format(config.motionBlurAmount)
        binding.labelDofAmount.text = "Depth of Field: %.2f".format(config.dofAmount)
        binding.labelDofFocus.text = "DOF Focus: %.2f".format(config.dofFocus)
        binding.labelDofSwirl.text = "DOF Swirl (Petzval): %.2f".format(config.dofSwirl)
        binding.labelRotBlur.text = "Rotational Blur: %.2f".format(config.rotationalBlurAmount)

        // Switches
        binding.switchGrain.isChecked = config.enableGrain
        binding.switchAutoLevels.isChecked = config.autoLevels
        binding.switchLightLeak.isChecked = config.lightLeakEnabled

        restoring = false
    }

    private fun saveConfig() {
        val prefs = requireContext()
            .getSharedPreferences(FilmrConfig.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        FilmrConfig.save(config, prefs)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
