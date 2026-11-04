package com.beamlink.peer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.view.WindowCompat;
import com.getcapacitor.BridgeActivity;
import java.util.List;

public class MainActivity extends BridgeActivity {

    private final ActivityResultLauncher<String[]> documentPicker =
            registerForActivityResult(
                    new ActivityResultContracts.OpenMultipleDocuments(),
                    this::onDocumentsPicked);

    /**
     * Registers plugins before the bridge starts.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ShareReceiverPlugin.class);
        registerPlugin(BeamlinkPlugin.class);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        handleShareIntent(getIntent());
        handleOpenTransfers(getIntent());
    }

    /**
     * Handles a new share while the activity is already open.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShareIntent(intent);
        handleOpenTransfers(intent);
    }

    /**
     * Opens OpenMultipleDocuments. Used by BeamlinkPlugin.pickFiles.
     */
    public void launchDocumentPicker() {
        documentPicker.launch(new String[] {"*/*"});
    }

    /**
     * Forwards picker URIs to the Capacitor plugin.
     */
    private void onDocumentsPicked(List<Uri> uris) {
        if (getBridge() == null) {
            return;
        }
        BeamlinkPlugin plugin = BeamlinkPlugin.Companion.from(
                getBridge().getPlugin("Beamlink")
        );
        if (plugin != null) {
            plugin.onDocumentsPicked(uris);
        }
    }

    /**
     * Forwards share intents to the Capacitor plugin.
     */
    private void handleShareIntent(Intent intent) {
        if (getBridge() == null) {
            return;
        }
        ShareReceiverPlugin plugin = ShareReceiverPlugin.Companion.from(
                getBridge().getPlugin("ShareReceiver")
        );
        if (plugin != null) {
            plugin.handleShareIntent(intent);
        }
    }

    /**
     * Opens the transfers screen when a disk-pause notification was tapped.
     */
    private void handleOpenTransfers(Intent intent) {
        if (intent == null) {
            return;
        }
        if (!intent.getBooleanExtra(
                com.beamlink.peer.receive.TransferDiskNotifier.EXTRA_OPEN_TRANSFERS,
                false)) {
            return;
        }
        intent.removeExtra(com.beamlink.peer.receive.TransferDiskNotifier.EXTRA_OPEN_TRANSFERS);
        if (getBridge() == null) {
            return;
        }
        BeamlinkPlugin plugin = BeamlinkPlugin.Companion.from(
                getBridge().getPlugin("Beamlink")
        );
        if (plugin != null) {
            plugin.openTransfers();
        }
    }
}
