package com.example.bloomybeauty.events

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

enum class OrderChangeKind { CREATED, CANCELLED, STATUS_CHANGED }
data class OrderChange(val orderId: Long, val ownerUserId: Long, val kind: OrderChangeKind)

fun interface OrderChangePublisher {
    fun publish(change: OrderChange)
    companion object { val None = OrderChangePublisher {} }
}

class BroadcastOrderChangePublisher(private val context: Context) : OrderChangePublisher {
    override fun publish(change: OrderChange) {
        // A notification failure must not turn a committed order into a failed checkout.
        try {
            context.sendBroadcast(Intent(action(context)).setPackage(context.packageName)
                .putExtra("order_id", change.orderId).putExtra("owner_user_id", change.ownerUserId)
                .putExtra("change_kind", change.kind.name))
        } catch (e: RuntimeException) { android.util.Log.e("OrderChanges", "Unable to send order change", e) }
    }
    companion object { fun action(context: Context) = context.packageName + ".action.ORDER_CHANGED" }
}

fun registerOrderChanges(context: Context, onChanged: (OrderChange) -> Unit): () -> Unit {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BroadcastOrderChangePublisher.action(context)) return
            val id = intent.getLongExtra("order_id", -1)
            val owner = intent.getLongExtra("owner_user_id", -1)
            val kind = OrderChangeKind.entries.find { it.name == intent.getStringExtra("change_kind") } ?: return
            if (id > 0 && owner > 0) onChanged(OrderChange(id, owner, kind))
        }
    }
    ContextCompat.registerReceiver(context, receiver, IntentFilter(BroadcastOrderChangePublisher.action(context)), ContextCompat.RECEIVER_NOT_EXPORTED)
    return { context.unregisterReceiver(receiver) }
}


// Publishers are post-commit side effects; callers must retain a successful business result.
fun OrderChangePublisher.publishAfterCommit(change: OrderChange) {
    try {publish(change)}
    catch(e: RuntimeException) {android.util.Log.e("OrderChanges","Post-commit delivery failed",e)}
}
