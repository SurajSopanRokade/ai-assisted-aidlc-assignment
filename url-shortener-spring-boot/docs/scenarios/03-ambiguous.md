# Scenario 3 — Ambiguous: *"add analytics and make it scalable"*

**Type:** under-specified requirement
**Requirement as given:** *"The service needs analytics and it should be
scalable and reliable."*

This is the scenario where the main risk is not writing bad code. It is writing
good code for the wrong problem, confidently, at speed — the failure mode that
AI assistance makes cheaper to commit.

---

## 1. What is actually unstated

| Phrase | What is missing | Why it blocks a decision |
| --- | --- | --- |
| "analytics" | Which metrics? Granularity? Retention? Who reads them? | A counter and a per-click event log differ by a schema migration and a backfill of data that was never captured. Choosing wrong is expensive in one direction only. |
| "scalable" | Reads or writes? What volume? Over what window? | Read scaling is a cache. Write scaling is a different database. They share no work. |
| "reliable" | Available? Durable? Correct under concurrency? | These pull apart. Exact click counts cost write latency; high availability under partition prefers approximate counts. |

## 2. Resolution strategy: decide by cost of being wrong

Waiting for answers was not an option — the requirement is the assignment. So
each ambiguity was resolved by asking **which mistake is cheaper to undo.**

**Analytics → per-click events, not just a counter.**
Wrong-and-cheap: I store rows nobody queries. Wrong-and-expensive: someone asks
"clicks per day last month" and the data never existed. Asymmetric, so I paid
the cheap cost. `click_events` exists; `last_clicked_at` is a `max()` over it.

**Scalable → make the constraint explicit, do not guess a number.**
With no target, any capacity claim would be invented. So instead of claiming
throughput, I:
1. made the read path index-bound (`short_code` unique index),
2. found and fixed the real limit — the write path failed at 100 concurrent
   redirects (80 of 100 returned 500),
3. documented the remaining ceiling honestly: SQLite has one writer, the pool is
   pinned to 1 so writers queue rather than fail, and PostgreSQL is a
   configuration change because the JPA boundary was kept clean.

That is a defensible answer to an unanswerable question: I cannot tell you the
QPS, but I can tell you exactly what the bottleneck is and what removes it.

**Reliable → correctness first, availability second.**
For a click counter, silently wrong numbers are worse than a brief outage —
nobody audits a number that looks plausible. So the atomic increment was chosen
over a faster in-memory path, and the trade-off is written down rather than
implied.

## 3. Assumptions register

Every one of these is a decision I made in the absence of an answer. They are
listed so a reviewer can disagree with the decision rather than reverse-engineer
it:

1. Analytics means count, created-at, last-clicked-at, expiry state. Geography,
   referrer and device are out of scope — the event table makes them additive.
2. Analytics are readable after expiry. Only redirection stops; the owner still
   needs the history.
3. "Scalable" is prototype scale, single instance. No QPS is claimed anywhere.
4. Exact counts beat low latency on the redirect path, at prototype volume.
5. Expiry uses server wall-clock time. Correct for one instance; cross-instance
   skew is an open item.

## 4. How this shows up in the product

`POST /copilot/analyze` runs a deterministic heuristic over any requirement and
flags language that cannot become an acceptance criterion. On the text above it
returns:

```json
{
  "ambiguities": [
    "'scalable' is used without a measurable definition — no target, threshold or SLA is given.",
    "'reliable' is used without a measurable definition — no target, threshold or SLA is given.",
    "'Analytics' is unspecified: which metrics, at what granularity, retained for how long?",
    "No quantitative target appears anywhere in the requirement (throughput, latency, retention or scale)."
  ]
}
```

**What this is honest about:** it is string matching, not comprehension. When it
finds nothing it says so explicitly — *"Pattern matching found no unquantified
language. This is a weak signal, not a clearance"* — because a checklist that
returns silence reads as approval, and a requirement can be entirely specific
and still be ambiguous about intent. It is a prompt for a human, and the code
says so.

## 5. What I would ask if I could

In priority order, because the first two change the architecture and the rest
only change the schedule:

1. Who reads the analytics, and to decide what? A dashboard and a billing
   system need different guarantees — one tolerates approximation, the other
   does not.
2. Expected redirects per second at peak, and the acceptable staleness for
   click counts. These two numbers alone decide SQLite vs PostgreSQL and
   synchronous vs batched writes.
3. Is a link private once created? That decides whether analytics need
   authorization, which is currently absent.
