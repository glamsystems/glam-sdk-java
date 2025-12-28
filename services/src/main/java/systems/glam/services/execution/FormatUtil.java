package systems.glam.services.execution;

import software.sava.core.accounts.PublicKey;
import software.sava.core.tx.Instruction;
import software.sava.core.tx.Transaction;
import software.sava.core.util.LamportDecimal;
import software.sava.rpc.json.http.response.IxError;
import software.sava.rpc.json.http.response.TransactionError;
import software.sava.rpc.json.http.response.TxSimulation;
import software.sava.services.solana.transactions.TransactionResult;
import systems.glam.sdk.idl.programs.glam.protocol.gen.GlamProtocolError;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

final class FormatUtil {

  public static String formatAccounts(final Instruction instruction) {
    return instruction.accounts().stream()
        .map(meta -> String.format(
            "%s:%s%s",
            meta.publicKey().toBase58(),
            meta.write() ? "w" : "r",
            meta.signer() ? "s" : ""
        ))
        .collect(Collectors.joining("\",\""));
  }

  private static String formatInstructions(final List<Instruction> instructions) {
    return instructions.stream().map(ix -> String.format("""
            {
              "p": "%s",
              "a": ["%s"],
              "d": "%s"
            }""",
        ix.programId().publicKey(),
        formatAccounts(ix),
        Base64.getEncoder().encodeToString(ix.data())
    )).collect(Collectors.joining(",\n"));
  }

  public static String formatInstructionException(final String event,
                                                  final List<Instruction> instructions,
                                                  final Collection<PublicKey> lookupTableKeys) {
    return String.format("""
            {
              "e": "%s",
              "ix": [
               %s
              ],
              "t": ["%s"]
            }
            """,
        event,
        formatInstructions(instructions),
        lookupTableKeys == null || lookupTableKeys.isEmpty()
            ? ""
            : lookupTableKeys.stream().map(PublicKey::toBase58).collect(Collectors.joining("\",\""))
    );
  }

  public static String formatInstructionException(final String event, final List<Instruction> instructions) {
    return formatInstructionException(event, instructions, null);
  }

  public static String formatLogs(final Collection<String> logs) {
    return logs.isEmpty()
        ? ""
        : logs.stream().collect(Collectors.joining("\",\n    \"", "\n    \"", "\""));
  }

  public static String formatSimulation(final String event,
                                        final Transaction tx,
                                        final TxSimulation txSimulation) {
    return String.format("""
            {
              "event": "%s",
              "tx": "%s",
              "logs": [%s
              ],
              "ix": [
               %s
              ]
            }
            """,
        event,
        tx.base64EncodeToString(),
        formatLogs(txSimulation.logs()),
        formatInstructions(tx.instructions())
    );
  }

  public static String formatTransactionResult(final TransactionResult transactionResult) {
    final int cuBudget = transactionResult.cuBudget();
    final long cuPrice = transactionResult.cuPrice();
    final long lamportsFee = transactionResult.totalFeeLamports();
    final var solFee = LamportDecimal.toBigDecimal(lamportsFee).stripTrailingZeros();
    final var transaction = transactionResult.transaction();
    final var numInstructions = transaction.instructions().size();
    final var formattedSig = transactionResult.formattedSig();
    final var sig = formattedSig == null || formattedSig.isBlank()
        ? "null"
        : '"' + formattedSig + '"';
    final var error = transactionResult.error();
    if (error != null) {
      final var simulation = transactionResult.txSimulation();
      final var logs = simulation == null
          ? ""
          : formatLogs(simulation.logs());

      final var builder = new StringBuilder();
      builder.append(String.format("""
              {
                "cuBudget": %d,
                "cuPrice": %d,
                "fee": %s,
                "size": %d,
                "numInstructions": %d,
                "logs": [%s
                ],
                "sig": %s,
                "tx": "%s",
              """,
          cuBudget, cuPrice, solFee.toPlainString(),
          transaction.size(), numInstructions,
          logs,
          sig,
          transaction.base64EncodeToString()
      ));
      if (error instanceof TransactionError.InstructionError(final int index, final var ixError)) {
        final var failedInstruction = transaction.instructions().get(index);
        String errorMsg;
        if (ixError instanceof IxError.Custom(final long errorCode)) {
          try {
            final var glamError = GlamProtocolError.getInstance((int) errorCode);
            errorMsg = glamError.msg();
          } catch (final RuntimeException ex) {
            errorMsg = error.toString();
          }
        } else {
          errorMsg = error.toString();
        }
        builder.append(String.format("""
                  "failedIx": {
                    "index": %d,
                    "programId": "%s",
                    "accounts": ["%s"],
                    "error": "%s"
                  }
                }""",
            index,
            failedInstruction.programId(),
            formatAccounts(failedInstruction),
            errorMsg
        ));
      } else {
        builder.append(String.format("""
              "error": "%s"
            }""", error
        ));
      }
      return builder.toString().indent(1);
    } else {
      return String.format("""
              {
                "cuBudget": %d,
                "cuPrice": %d,
                "fee": %s,
                "size": %d,
                "numInstructions": %d,
                "sig": %s
               }""",
          cuBudget, cuPrice, solFee.toPlainString(),
          transaction.size(), numInstructions, sig
      ).indent(1);
    }
  }

  public static String formatDuration(final Duration duration) {
    return duration.toString().substring(2);
  }

  public static String formatDuration(final Duration duration, final ChronoUnit truncateUnit) {
    return duration
        .truncatedTo(truncateUnit)
        .toString()
        .substring(2);
  }

  public static String formatDuration(final long millis, final ChronoUnit truncateUnit) {
    return formatDuration(Duration.ofMillis(millis), truncateUnit);
  }

  private FormatUtil() {
  }

  public static String parseFixLengthString(final byte[] chars) {
    for (int i = chars.length - 1, c; i >= 0; --i) {
      c = chars[i] & 0xFF;
      if (c != 0 && !Character.isWhitespace(c)) {
        return new String(chars, 0, i + 1);
      }
    }
    return new String(chars);
  }
}
