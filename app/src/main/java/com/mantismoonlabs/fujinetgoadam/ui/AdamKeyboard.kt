package com.mantismoonlabs.fujinetgoadam.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mantismoonlabs.fujinetgoadam.SessionController
import com.mantismoonlabs.fujinetgoadam.input.AdamKeys

/**
 * A compact on-screen ADAM keyboard. Keys inject ASCII-compatible bytes that the
 * EOS keyboard driver consumes (see Coleco.c::AddToKeyboardBuffer). A Shift
 * toggle flips letter case and the number row to symbols.
 */
@Composable
fun AdamKeyboard(
    session: SessionController,
    modifier: Modifier = Modifier,
) {
    var shift by remember { mutableStateOf(false) }

    val rows = listOf(
        if (shift) "!@#\$%^&*()" else "1234567890",
        if (shift) "QWERTYUIOP" else "qwertyuiop",
        if (shift) "ASDFGHJKL" else "asdfghjkl",
        if (shift) "ZXCVBNM,." else "zxcvbnm,.",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (c in row) {
                    Key(c.toString(), Modifier.width(34.dp)) { session.key(AdamKeys.char(c)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Key(if (shift) "SHIFT*" else "shift", Modifier.width(64.dp)) { shift = !shift }
            Key("space", Modifier.width(150.dp)) { session.key(AdamKeys.SPACE) }
            Key("DEL", Modifier.width(52.dp)) { session.key(AdamKeys.BACKSPACE) }
            Key("RTN", Modifier.width(52.dp)) { session.key(AdamKeys.RETURN) }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier = Modifier, onTap: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
            .clickable { onTap() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}
