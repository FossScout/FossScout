package com.fossscout.feature.companion

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Renders the exact canonical repository payload; no camera permission is used. */
@Composable
fun RepositoryQrCode(
    payload: String,
    modifier: Modifier = Modifier,
    size: Dp = 240.dp,
) {
    val image = remember(payload) { generateQr(payload) }
    if (image == null) {
        Text(
            text = stringResource(R.string.companion_qr_unavailable),
            modifier = modifier,
        )
    } else {
        Image(
            bitmap = image,
            contentDescription = stringResource(R.string.companion_qr_description),
            modifier = modifier
                .size(size)
                .background(Color.White)
                .padding(8.dp),
        )
    }
}

private fun generateQr(payload: String): ImageBitmap? = runCatching {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE)
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        val x = index % matrix.width
        val y = index / matrix.width
        if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    Bitmap.createBitmap(
        pixels,
        matrix.width,
        matrix.height,
        Bitmap.Config.ARGB_8888,
    ).asImageBitmap()
}.getOrNull()

private const val QR_SIZE = 512
