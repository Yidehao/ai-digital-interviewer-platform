# Transcripts to score

Twelve candidates, same two questions. Score each one on the sheet before moving to the
next, and **do not go back and adjust** - a first pass you then normalise is one judgement
applied twelve times, not twelve judgements.

You are not told anything about these candidates. That is deliberate.

---

## Candidate A

**Q: How would you add a cache in front of a read-heavy endpoint?**

I'd use Redis with a TTL on the read endpoint. You have to think about invalidation as well, since the data can go stale.

**Q: A query that used to be fast is now taking eight seconds. Where do you start?**

I'd look at the execution plan first and check whether it's using an index. Usually when a query slows down like that it's an index problem, or the table has grown.

---

## Candidate B

**Q: How would you add a cache in front of a read-heavy endpoint?**

So the way I'd approach this, and I have done this before, is to put Redis in front of the read path. For the TTL I would go with 30 seconds. Now the thing people underestimate about caching is that the hard part is really the invalidation. What we ended up doing was publishing invalidation events on write rather than relying on expiry alone, and the reasoning was that stale pricing was worse than a cache miss.

**Q: A query that used to be fast is now taking eight seconds. Where do you start?**

This is something I ran into recently and it took a while to track down. The query did have a composite index, specifically on tenant_id and created_at, which you would think would be enough. But the ORM was generating an order by on updated_at, not created_at, and because of that mismatch the database ended up doing a filesort over roughly 400,000 rows. Once we added the index that matched what the ORM was asking for, it went from eight seconds down to forty milliseconds.

---

## Candidate C

**Q: How would you add a cache in front of a read-heavy endpoint?**

So for something like this, what I would probably do is add a cache in front of it. There are a few options but Redis is one that people use. The main thing is that it should make it faster, which is what we want here.

**Q: A query that used to be fast is now taking eight seconds. Where do you start?**

So in terms of query performance, the main thing that we relied on was indexes. Indexes are generally the thing that makes queries faster, so that would be where I would look for something like this.

---

## Candidate D

**Q: How would you add a cache in front of a read-heavy endpoint?**

Add a cache. Redis probably. Makes it faster.

**Q: A query that used to be fast is now taking eight seconds. Where do you start?**

Indexes. They make queries faster.

---

## Candidate E

**Q: How would you add a cache in front of a read-heavy endpoint?**

I will just add the cache in front, maybe Redis or something. It should be more fast.

**Q: A query that used to be fast is now taking eight seconds. Where do you start?**

We are using index. Normally it makes the query more fast.

---

## Candidate F

**Q: How would you add a cache in front of a read-heavy endpoint?**

I'd just add a cache in front of it, probably Redis or something. That should make it faster.

**Q: A query that used to be fast is now taking eight seconds. Where do you start?**

We used indexes. That normally makes queries faster.

---

## Candidate G

**Q: How would you add a cache in front of a read-heavy endpoint?**

Redis on the read path, 30s TTL. Invalidation's the hard part. We published invalidation events on write instead of relying on expiry - stale pricing beats a cache miss.

**Q: A query that used to be fast is now taking eight seconds. Where do you start?**

Composite index was on (tenant_id, created_at). ORM ordered by updated_at. Filesort over 400k rows. Matching index: 8s to 40ms.

---

## Candidate H

**Q: How would you add a cache in front of a read-heavy endpoint?**

I will put the Redis in front of read path, TTL of 30 second. Difficult part is invalidation. We are publishing invalidation event when write happens, not depend on expiry only, because stale price is more bad than cache miss.

**Q: A query that used to be fast is now taking eight seconds. Where do you start?**

The query is having composite index on tenant_id and created_at, but ORM was doing order by updated_at, so it makes filesort on 400,000 rows. After adding correct index, it become from eight second to forty millisecond.

---

## Candidate I

**Q: How would you add a cache in front of a read-heavy endpoint?**

So for this kind of problem I would generally reach for Redis. You would set a TTL on the read endpoint, and that handles a lot of it. The other thing you have to think about, and this is something that comes up, is invalidation, because the data can go stale over time.

**Q: A query that used to be fast is now taking eight seconds. Where do you start?**

The first thing I would do in a situation like this is pull up the execution plan, because that tells you what the database is actually doing. Then I would check whether it is using an index or not. In my experience, when a query slows down like that, it is usually either an index problem or the table has simply grown over time.

---

## Candidate J

**Q: How would you add a cache in front of a read-heavy endpoint?**

I'd put Redis in front of the read path with a 30 second TTL. The hard part is invalidation. We published invalidation events on write rather than relying on expiry, because stale pricing was worse than a cache miss.

**Q: A query that used to be fast is now taking eight seconds. Where do you start?**

The query had a composite index on tenant_id and created_at, but the ORM was ordering by updated_at, so it did a filesort over 400,000 rows. Adding the matching index took it from eight seconds to forty milliseconds.

---

## Candidate K

**Q: How would you add a cache in front of a read-heavy endpoint?**

I will use the Redis with TTL on read endpoint. Also need to think about invalidation, because data can become stale.

**Q: A query that used to be fast is now taking eight seconds. Where do you start?**

I will look at execution plan first, check if index is used. Usually when query become slow like this, is index problem, or table is grown too big.

---

## Candidate L

**Q: How would you add a cache in front of a read-heavy endpoint?**

Redis, TTL on the read endpoint. Invalidation matters too - data goes stale.

**Q: A query that used to be fast is now taking eight seconds. Where do you start?**

Check the execution plan. Look for a missing index. Or the table grew.

---
