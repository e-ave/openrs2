package org.openrs2.protocol.create.downstream

import org.openrs2.protocol.EmptyPacketCodec
import javax.inject.Singleton

@Singleton
public class PasswordInvalidLengthCodec : EmptyPacketCodec<CreateResponse.PasswordInvalidLength>(
    packet = CreateResponse.PasswordInvalidLength,
    opcode = 30
)