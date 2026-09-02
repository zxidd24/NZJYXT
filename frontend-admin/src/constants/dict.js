export const orderStatusDict = {
  0: '待付款', 1: '待审核', 2: '待发货', 3: '待收货',
  4: '已完成', 5: '已取消', 6: '退款中', 7: '已退款',
}

export const productStatusDict = { 0: '待审核', 1: '已上架', 2: '已下架', 3: '审核驳回' }
export const authStatusDict = { 0: '未认证', 1: '审核中', 2: '已认证', 3: '驳回' }
export const auditStatusDict = { 0: '待审核', 1: '已通过', 2: '已驳回' }
export const loanStatusDict = { 0: '申请中', 1: '已放款', 2: '已还款', 3: '驳回' }
export const payStatusDict = { 0: '未支付', 1: '已支付' }
export const payMethodDict = { WALLET: '钱包', BANK_TRANSFER: '银行转账', CREDIT: '额度' }
export const refundStatusDict = { 0: '待审核', 1: '同意', 2: '驳回', 3: '已退款' }
export const invoiceStatusDict = { 0: '待开票', 1: '已开票', 2: '驳回' }
export const walletTransactionTypeDict = { 1: '入金', 2: '出金', 3: '支付', 4: '退款' }
export const walletDirectionDict = { 1: '入账', 2: '出账' }
export const messageReadStatusDict = { 0: '未读', 1: '已读' }
