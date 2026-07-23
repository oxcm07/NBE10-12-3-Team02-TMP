package com.back.domain.waiting.service

import com.back.domain.waiting.dto.QueueStatusDto
import com.back.global.exception.ErrorCode
import com.back.global.exception.ServiceException
import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class WaitingQueueManager(
    private val redissonClient: RedissonClient
) {

    fun registerWaiting(scheduleId: Long, userId: Long): Long {
        val waitKey = generateWaitKey(scheduleId)
        val seqKey = generateSequenceKey(scheduleId)
        val user = userId.toString()

        val activeSchedules = redissonClient.getSet<String>(ACTIVE_SCHEDULES_KEY)
        activeSchedules.add(scheduleId.toString())

        val script = redissonClient.getScript(StringCodec.INSTANCE)
        val rank: Long? = script.eval(
            RScript.Mode.READ_WRITE,
            """
            local exists = redis.call('ZSCORE', KEYS[1], ARGV[1])
            if not exists then
              local sequence = redis.call('INCR', KEYS[2])
              redis.call('ZADD', KEYS[1], sequence, ARGV[1])
            end
            local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
            if not rank then
              return -1
            end
            return rank + 1
            """.trimIndent(),
            RScript.ReturnType.LONG,
            listOf(waitKey, seqKey),
            user
        )

        if (rank == null || rank < 1) {
            throw ServiceException(ErrorCode.WAITING_QUEUE_REGISTER_FAILED)
        }
        return rank
    }

    fun showWaitingRank(scheduleId: Long, userId: Long): Long {
        val waitSet = redissonClient.getScoredSortedSet<String>(generateWaitKey(scheduleId))
        val rank = waitSet.rank(userId.toString()) ?: throw ServiceException(ErrorCode.WAITING_QUEUE_NOT_FOUND)
        return rank + 1L
    }

    fun cancelWaiting(scheduleId: Long, userId: Long): Boolean {
        val waitSet = redissonClient.getScoredSortedSet<String>(generateWaitKey(scheduleId))
        return waitSet.remove(userId.toString())
    }

    fun cancelActiveUser(scheduleId: Long, userId: Long): Boolean {
        val activeSet = redissonClient.getScoredSortedSet<String>(generateQueueActiveKey(scheduleId))
        val removed = activeSet.remove(userId.toString())
        redissonClient.getBucket<String>(generateActiveTokenKey(scheduleId, userId)).delete()
        return removed
    }

    fun removeActiveUser(scheduleId: Long, userId: Long) {
        val activeSet = redissonClient.getScoredSortedSet<String>(generateQueueActiveKey(scheduleId))
        activeSet.remove(userId.toString())
        redissonClient.getBucket<String>(generateActiveTokenKey(scheduleId, userId)).delete()
    }

    fun removeExpiredActiveUsers(scheduleId: Long): Long {
        val activeSet = redissonClient.getScoredSortedSet<String>(generateQueueActiveKey(scheduleId))
        val removed = activeSet.removeRangeByScore(0.0, true, System.currentTimeMillis().toDouble(), true)
        return removed.toLong()
    }

    fun addActiveUser(scheduleId: Long, capacity: Long, batchSize: Int, ttl: Duration): List<Long> {
        val waitKey = generateWaitKey(scheduleId)
        val activeKey = generateQueueActiveKey(scheduleId)
        val now = System.currentTimeMillis()
        val expiredAt = now + ttl.toMillis()

        val script = redissonClient.getScript(StringCodec.INSTANCE)
        val userIds: List<String>? = script.eval(
            RScript.Mode.READ_WRITE,
            """
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', tonumber(ARGV[3]))
            local activeCount = redis.call('ZCARD', KEYS[2])
            local availableSlots = tonumber(ARGV[1]) - activeCount
            if availableSlots <= 0 then
              return {}
            end
            local popCount = math.min(availableSlots, tonumber(ARGV[2]))
            local users = redis.call('ZRANGE', KEYS[1], 0, popCount - 1)
            if #users == 0 then
              return {}
            end
            redis.call('ZREM', KEYS[1], unpack(users))
            for _, u in ipairs(users) do
              redis.call('ZADD', KEYS[2], tonumber(ARGV[4]), u)
            end
            return users
            """.trimIndent(),
            RScript.ReturnType.LIST,
            listOf(waitKey, activeKey),
            capacity.toString(),
            batchSize.toString(),
            now.toString(),
            expiredAt.toString()
        )

        if (userIds.isNullOrEmpty()) {
            return emptyList()
        }
        return userIds.map { it.toLong() }
    }

    fun issueToken(scheduleId: Long, userId: Long, ttl: Duration): String {
        val entryToken = java.util.UUID.randomUUID().toString()
        val bucket = redissonClient.getBucket<String>(generateActiveTokenKey(scheduleId, userId))
        bucket.set(entryToken, ttl)
        return entryToken
    }

    fun getActiveToken(scheduleId: Long, userId: Long): String? {
        val activeSet = redissonClient.getScoredSortedSet<String>(generateQueueActiveKey(scheduleId))
        val score = activeSet.getScore(userId.toString())
        if (score == null || score <= System.currentTimeMillis()) {
            return null
        }
        val bucket = redissonClient.getBucket<String>(generateActiveTokenKey(scheduleId, userId))
        return bucket.get()
    }

    fun getQueueStatus(scheduleId: Long): QueueStatusDto {
        val waitSet = redissonClient.getScoredSortedSet<String>(generateWaitKey(scheduleId))
        val totalWaitingCount = waitSet.size().toLong()
        if (totalWaitingCount == 0L) {
            return QueueStatusDto(0L, 0L)
        }

        val firstScore = waitSet.firstScore()
        val currentAllowedSequence = if (firstScore != null) firstScore.toLong() - 1 else 0L
        return QueueStatusDto(currentAllowedSequence, totalWaitingCount)
    }

    fun getQueueSequence(scheduleId: Long, userId: Long): Long {
        val waitSet = redissonClient.getScoredSortedSet<String>(generateWaitKey(scheduleId))
        val score = waitSet.getScore(userId.toString())
        return score?.toLong() ?: 0L
    }

    fun isQueueEmpty(scheduleId: Long): Boolean {
        val waitSet = redissonClient.getScoredSortedSet<String>(generateWaitKey(scheduleId))
        val activeSet = redissonClient.getScoredSortedSet<String>(generateQueueActiveKey(scheduleId))
        return waitSet.isEmpty && activeSet.isEmpty
    }

    fun clearWaitingQueue(scheduleId: Long) {
        redissonClient.getScoredSortedSet<String>(generateWaitKey(scheduleId)).delete()
    }

    fun getActiveScheduleIds(): Set<String> {
        val activeSchedules = redissonClient.getSet<String>(ACTIVE_SCHEDULES_KEY)
        return activeSchedules.readAll() ?: emptySet()
    }

    fun removeFromActiveSchedules(scheduleIdStr: String) {
        val activeSchedules = redissonClient.getSet<String>(ACTIVE_SCHEDULES_KEY)
        activeSchedules.remove(scheduleIdStr)
    }

    private fun generateWaitKey(scheduleId: Long): String = "$WAIT_KEY_PREFIX$scheduleId"
    private fun generateSequenceKey(scheduleId: Long): String = "$SEQUENCE_KEY_PREFIX$scheduleId"

    companion object {
        private const val WAIT_KEY_PREFIX = "queue:wait:schedule:"
        private const val SEQUENCE_KEY_PREFIX = "queue:wait:sequence:schedule:"
        private const val ACTIVE_TOKEN_KEY_PREFIX = "queue:active:token:"
        private const val ACTIVE_SCHEDULES_KEY = "queue:active:schedules"

        @JvmStatic
        fun generateActiveTokenKey(scheduleId: Long, userId: Long): String =
            "$ACTIVE_TOKEN_KEY_PREFIX$scheduleId:$userId"

        @JvmStatic
        fun generateQueueActiveKey(scheduleId: Long): String =
            "queue:active:schedule:$scheduleId"
    }
}
