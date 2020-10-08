package org.wordpress.android.ui.mediapicker.insert

import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.ui.mediapicker.MediaPickerSetup
import org.wordpress.android.ui.mediapicker.MediaPickerSetup.DataSource.DEVICE
import org.wordpress.android.ui.mediapicker.MediaPickerSetup.DataSource.GIF_LIBRARY
import org.wordpress.android.ui.mediapicker.MediaPickerSetup.DataSource.STOCK_LIBRARY
import org.wordpress.android.ui.mediapicker.MediaPickerSetup.DataSource.WP_LIBRARY
import org.wordpress.android.ui.mediapicker.insert.StockMediaInsertUseCase.StockMediaInsertUseCaseFactory
import javax.inject.Inject

class MediaInsertHandlerFactory
@Inject constructor(
    private val deviceListInsertUseCase: DeviceListInsertUseCase,
    private val stockMediaInsertUseCaseFactory: StockMediaInsertUseCaseFactory
) {
    fun build(mediaPickerSetup: MediaPickerSetup, siteModel: SiteModel?): MediaInsertHandler {
        return when (mediaPickerSetup.dataSource) {
            DEVICE -> deviceListInsertUseCase
            WP_LIBRARY -> DefaultMediaInsertUseCase
            STOCK_LIBRARY -> stockMediaInsertUseCaseFactory.build(requireNotNull(siteModel) {
                "Site is necessary when inserting into stock media library "
            })
            GIF_LIBRARY -> DefaultMediaInsertUseCase
        }.toMediaInsertHandler()
    }

    private fun MediaInsertUseCase.toMediaInsertHandler() = MediaInsertHandler(this)

    private object DefaultMediaInsertUseCase : MediaInsertUseCase
}
