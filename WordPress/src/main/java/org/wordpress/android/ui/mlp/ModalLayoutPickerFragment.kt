package org.wordpress.android.ui.mlp

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.android.synthetic.main.modal_layout_picker_bottom_toolbar.*
import kotlinx.android.synthetic.main.modal_layout_picker_fragment.*
import kotlinx.android.synthetic.main.modal_layout_picker_header.*
import org.wordpress.android.R
import org.wordpress.android.WordPress
import org.wordpress.android.util.DisplayUtils
import org.wordpress.android.util.WPActivityUtils
import org.wordpress.android.util.setVisible
import org.wordpress.android.viewmodel.Event
import org.wordpress.android.viewmodel.mlp.ModalLayoutPickerViewModel
import javax.inject.Inject

/**
 * Implements the Modal Layout Picker UI based on the [BottomSheetDialogFragment] to inherit the container behavior
 */
class ModalLayoutPickerFragment : BottomSheetDialogFragment() {
    @Inject lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var viewModel: ModalLayoutPickerViewModel

    companion object {
        const val MODAL_LAYOUT_PICKER_TAG = "MODAL_LAYOUT_PICKER_TAG"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.modal_layout_picker_fragment, container)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView.layoutManager = LinearLayoutManager(requireActivity())
        recyclerView.adapter = ModalLayoutPickerAdapter()

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            /**
             * We track the vertical scroll to show/hide the header title accordingly
             */
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                setHeaderVisibility()
            }
        })

        backButton.setOnClickListener {
            closeModal()
        }

        createBlankPageButton.setOnClickListener {
            closeModal()
            viewModel.createPage()
        }
        createPageButton.setOnClickListener { /* TODO */ }
        previewButton.setOnClickListener { /* TODO */ }

        setupViewModel()

        setHeaderVisibility()
    }

    private fun setHeaderVisibility() {
        if (DisplayUtils.isLandscape(context)) {
            title.setVisible(true)
        } else {
            val scrollThreshold = resources.getDimension(R.dimen.mlp_header_scroll_snap_threshold).toInt()
            val offset = recyclerView.computeVerticalScrollOffset()
            viewModel.setHeaderTitleVisibility(offset > scrollThreshold)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?) = BottomSheetDialog(requireContext(), getTheme()).apply {
        fillTheScreen(this)
        setStatusBarColor(this)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        (requireActivity().applicationContext as WordPress).component().inject(this)
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        closeModal()
    }

    private fun closeModal() {
        WPActivityUtils.setLightStatusBar(activity?.window, false)
        viewModel.dismiss()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory)
                .get(ModalLayoutPickerViewModel::class.java)

        viewModel.listItems.observe(this, Observer {
            (recyclerView?.adapter as? ModalLayoutPickerAdapter)?.update(it ?: listOf())
        })

        viewModel.isHeaderVisible.observe(this,
                Observer { event: Event<Boolean> ->
                    event.applyIfNotHandled {
                        title.setVisible(this)
                    }
                }
        )

        viewModel.buttonsUiState.observe(this,
                Observer {
                    createBlankPageButton.setVisible(it.createBlankPageVisible)
                    createPageButton.setVisible(it.createPageVisible)
                    previewButton.setVisible(it.previewVisible)
                }
        )
    }

    private fun fillTheScreen(dialog: BottomSheetDialog) {
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val parentLayout =
                    bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            parentLayout?.let { it ->
                val behaviour = BottomSheetBehavior.from(it)
                setupFullHeight(it)
                behaviour.skipCollapsed = true
                behaviour.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    private fun setupFullHeight(bottomSheet: View) {
        val layoutParams = bottomSheet.layoutParams
        layoutParams.height = WindowManager.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = layoutParams
    }

    private fun setStatusBarColor(dialog: BottomSheetDialog) {
        dialog.behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                WPActivityUtils.setLightStatusBar(activity?.window, newState == BottomSheetBehavior.STATE_EXPANDED)
            }
        })
    }
}
