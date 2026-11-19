<script setup>
import {onLaunch} from '@dcloudio/uni-app'
import {useAuthStore} from './stores/auth'

onLaunch(async () => {
  const auth = useAuthStore()
  await auth.ensureLogin()
})
</script>

<style>
page {
  --pri: #4f6ef7;
  --pri-dim: #eef1fe;
  --ink: #1f2430;
  --sub: #8a90a3;
  --line: #eceef4;
  --bg: #f6f7fb;
  --ok: #22b07d;
  --bad: #e85454;
  --warn: #f0a020;
  background: var(--bg);
  color: var(--ink);
  font-size: 28rpx;
  line-height: 1.6;
}

.card {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  margin: 24rpx;
}

.btn-pri {
  background: var(--pri);
  color: #fff;
  border-radius: 48rpx;
  padding: 22rpx 0;
  text-align: center;
  font-size: 30rpx;
}

.btn-gho {
  background: #fff;
  color: var(--pri);
  border: 2rpx solid var(--pri);
  border-radius: 48rpx;
  padding: 20rpx 0;
  text-align: center;
}

.btn-dis {
  opacity: .45;
}

.sub {
  color: var(--sub);
  font-size: 24rpx;
}

.tag {
  display: inline-block;
  font-size: 20rpx;
  padding: 2rpx 14rpx;
  border-radius: 8rpx;
  background: var(--pri-dim);
  color: var(--pri);
  margin-right: 12rpx;
}

.tag.warn {
  background: #fdf3e3;
  color: var(--warn);
}

.empty {
  text-align: center;
  color: var(--sub);
  padding: 120rpx 0;
}
</style>
