package viewmodel

import com.example.viewmodel.SonoraViewModel

/**
 * PlayerViewModel alias referencing the shared SonoraViewModel playback architecture.
 * Guarantees a single shared playback engine without duplicating state or services.
 */
typealias PlayerViewModel = SonoraViewModel
