# Amplitude Payment Compensation Sandbox

Required release scenarios:

1. release succeeds;
2. reservation already released;
3. reservation expired;
4. reservation already consumed by posting;
5. timeout after provider commit;
6. duplicate idempotency key.

Required reversal scenarios:

1. reversal succeeds;
2. reversal rejected;
3. reversal not allowed;
4. already reversed;
5. authorization missing or invalid;
6. timeout after provider commit;
7. duplicate idempotency key;
8. lookup confirms final outcome.

Never treat a timeout as proof that no financial effect occurred.
