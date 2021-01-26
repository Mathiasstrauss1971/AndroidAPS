package info.nightscout.androidaps.plugins.pump.virtual

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.VirtualpumpFragmentBinding
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.virtual.events.EventVirtualPumpUpdateGui
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class VirtualPumpFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin

    private val disposable = CompositeDisposable()

    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGui() }
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    private var _binding: VirtualpumpFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = VirtualpumpFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventVirtualPumpUpdateGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, { fabricPrivacy.logException(it) })
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Synchronized
    private fun updateGui() {
        if (_binding == null) return
        binding.basabasalrate.text = resourceHelper.gs(R.string.pump_basebasalrate, virtualPumpPlugin.baseBasalRate)
        binding.tempbasal.text = treatmentsPlugin.getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull()
            ?: ""
        binding.extendedbolus.text = treatmentsPlugin.getExtendedBolusFromHistory(System.currentTimeMillis())?.toString()
            ?: ""
        binding.battery.text = resourceHelper.gs(R.string.format_percent, virtualPumpPlugin.batteryPercent)
        binding.reservoir.text = resourceHelper.gs(R.string.formatinsulinunits, virtualPumpPlugin.reservoirInUnits.toDouble())

        virtualPumpPlugin.refreshConfiguration()
        val pumpType = virtualPumpPlugin.pumpType

        binding.type.text = pumpType?.description
        binding.typeDef.text = pumpType?.getFullDescription(resourceHelper.gs(R.string.virtualpump_pump_def), pumpType.hasExtendedBasals(), resourceHelper)
    }
}
