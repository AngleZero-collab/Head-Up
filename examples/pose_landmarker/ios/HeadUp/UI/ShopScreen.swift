import SwiftUI

struct ShopScreen: View {
    @ObservedObject var store: HeadUpStore
    var onChallenges: () -> Void = {}
    @State private var collectionOnly = false
    @State private var pendingItem: ShopItem?
    @State private var purchasedName: String?
    @Environment(\.dynamicTypeSize) private var typeSize
    private var copy: HeadUpCopy { HeadUpCopy(language: store.language) }
    private var visibleItems: [ShopItem] { store.shopItems.filter { !collectionOnly || $0.isOwned } }

    var body: some View {
        HeadUpPage {
            HeadUpSectionTitle(title: copy.text("Good habits, well rewarded", "好習慣，值得獎勵"), symbol: "bag")
            HeadUpCard {
                VStack(alignment: .leading, spacing: 14) {
                    Label(copy.text("Available points", "可用積分"), systemImage: "star.circle.fill")
                        .foregroundStyle(HeadUpStyle.secondary)
                    Text(store.coins.formatted())
                        .font(.largeTitle.bold()).monospacedDigit().foregroundStyle(HeadUpStyle.warning)
                    ViewThatFits(in: .horizontal) {
                        HStack { collectionCount; Spacer(minLength: 16); earnButton }
                            .fixedSize(horizontal: true, vertical: false)
                        VStack(alignment: .leading, spacing: 8) { collectionCount; earnButton }
                    }
                }
            }
            if let purchasedName {
                Label(copy.text("Added to your collection: \(purchasedName)", "已加入收藏：\(purchasedName)"), systemImage: "checkmark.seal.fill")
                    .font(.subheadline.weight(.medium)).foregroundStyle(HeadUpStyle.safe)
                    .accessibilityIdentifier("purchaseSuccess")
            }
            if typeSize.isAccessibilitySize {
                collectionPicker.pickerStyle(.menu)
                    .frame(minHeight: 44)
            } else {
                collectionPicker.pickerStyle(.segmented)
            }
            if visibleItems.isEmpty {
                ContentUnavailableView {
                    Label(copy.text("Your collection starts here", "開始你的收藏"), systemImage: "gift")
                } description: {
                    Text(copy.text("Complete daily challenges, claim points, then choose a reward.", "完成每日挑戰並領取積分，再挑選喜歡的獎勵。"))
                } actions: {
                    earnButton
                }
            } else {
                ForEach(visibleItems) { item in shopRow(item) }
            }
            Text(copy.text("Points and collectibles are saved on this device. No payment is required. Collectibles do not change the dragon's appearance or unlock phone usage.", "積分與收藏儲存在本機，不需要付款。收藏不會改變視力龍外觀，也不會解鎖手機使用時間。"))
                .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
        }
        .accessibilityIdentifier("shopScreen")
        .onDisappear { purchasedName = nil; pendingItem = nil }
        .onChange(of: store.ownedShopItems) { _, owned in
            if owned.isEmpty { purchasedName = nil }
        }
        .alert(copy.text("Confirm redemption", "確認兌換"), isPresented: Binding(
            get: { pendingItem != nil }, set: { if !$0 { pendingItem = nil } }
        ), presenting: pendingItem) { item in
            Button(copy.text("Redeem for \(item.cost) points", "使用 \(item.cost) 點兌換")) {
                // The store rechecks ownership/balance and saves the debit atomically.
                if store.purchaseItem(item.id) { purchasedName = title(item.id) }
                pendingItem = nil
            }
            .disabled(store.coins < item.cost || store.ownedShopItems.contains(item.id))
            Button(copy.text("Cancel", "取消"), role: .cancel) { pendingItem = nil }
        } message: { item in
            Text(copy.text("\(title(item.id))\nCost: \(item.cost) points\nRemaining: \(max(0, store.coins - item.cost)) points", "\(title(item.id))\n需要：\(item.cost) 點\n兌換後剩餘：\(max(0, store.coins - item.cost)) 點"))
        }
    }

    private var collectionPicker: some View {
        Picker(copy.text("Rewards filter", "獎勵篩選"), selection: $collectionOnly) {
            Text(copy.text("All rewards", "所有獎勵")).tag(false)
            Text(copy.text("My collection", "我的收藏")).tag(true)
        }
        .accessibilityIdentifier("shopCollectionFilter")
    }

    private var collectionCount: some View {
        Text(copy.text("\(store.ownedShopItems.count) collected", "已收藏 \(store.ownedShopItems.count) 項"))
            .font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
    }

    private var earnButton: some View {
        Button(action: onChallenges) {
            Label(copy.text("Earn points", "前往挑戰賺積分"), systemImage: "arrow.right.circle")
                .frame(minHeight: 44)
        }
        .buttonStyle(.bordered)
        .accessibilityIdentifier("shopEarnPoints")
    }

    private func shopRow(_ item: ShopItem) -> some View {
        HeadUpCard {
            VStack(alignment: .leading, spacing: 16) {
                HStack(alignment: .top, spacing: 14) {
                    Image(systemName: symbol(item.id))
                        .font(.title2)
                        .foregroundStyle(HeadUpStyle.purple)
                        .frame(width: 52, height: 52)
                        .background(HeadUpStyle.purple.opacity(0.14), in: RoundedRectangle(cornerRadius: 16))
                    VStack(alignment: .leading, spacing: 5) {
                        Text(title(item.id)).font(.headline)
                        Text(detail(item.id)).font(.subheadline).foregroundStyle(HeadUpStyle.secondary)
                    }
                    Spacer(minLength: 0)
                }
                ViewThatFits(in: .horizontal) {
                    HStack {
                        price(item); Spacer(minLength: 16); redemptionButton(item)
                    }
                    .fixedSize(horizontal: true, vertical: false)
                    VStack(alignment: .leading, spacing: 8) { price(item); redemptionButton(item) }
                }
                if !item.isOwned && store.coins < item.cost {
                    Text(copy.text("\(item.cost - store.coins) more points needed. Earn them in Challenges.", "還差 \(item.cost - store.coins) 點，可在挑戰頁累積。"))
                        .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
                }
            }
        }
    }

    private func price(_ item: ShopItem) -> some View {
        Label(copy.text("\(item.cost) points", "\(item.cost) 點"), systemImage: "star.fill")
            .font(.subheadline.bold()).foregroundStyle(HeadUpStyle.warning)
    }

    private func redemptionButton(_ item: ShopItem) -> some View {
        Button { purchasedName = nil; pendingItem = item } label: {
            Label(buttonTitle(item), systemImage: item.isOwned ? "checkmark" : "gift")
                .font(.subheadline.bold()).frame(minHeight: 44)
        }
        .buttonStyle(.bordered)
        .tint(item.isOwned ? HeadUpStyle.safe : HeadUpStyle.cyan)
        .disabled(item.isOwned || store.coins < item.cost)
        .accessibilityIdentifier("purchase_\(item.id)")
    }

    private func buttonTitle(_ item: ShopItem) -> String {
        if item.isOwned { return copy.text("Owned", "已擁有") }
        if store.coins < item.cost { return copy.text("More points needed", "積分不足") }
        return copy.text("Redeem", "兌換")
    }

    private func title(_ id: String) -> String {
        switch id {
        case "starlight_armor": return copy.text("Starlight armor", "星光護甲")
        case "ocean_background": return copy.text("Ocean background", "海藍背景")
        case "eye_time_ticket": return copy.text("Eye-rest time ticket", "護眼時間券")
        default: return copy.text("Focus badge", "專注徽章")
        }
    }

    private func detail(_ id: String) -> String {
        switch id {
        case "starlight_armor": return copy.text("A keepsake for caring for your posture", "紀念你照顧姿勢的收藏品")
        case "ocean_background": return copy.text("An ocean-inspired collectible card", "海藍主題收藏卡")
        case "eye_time_ticket": return copy.text("A 30-minute reward to arrange yourself; it does not control screen time.", "可自行安排的 30 分鐘獎勵紀念券，不會控制螢幕使用時間。")
        default: return copy.text("Celebrate your focus", "為專注努力留下紀念")
        }
    }

    private func symbol(_ id: String) -> String {
        switch id {
        case "starlight_armor": return "shield.lefthalf.filled"
        case "ocean_background": return "water.waves"
        case "eye_time_ticket": return "ticket"
        default: return "medal"
        }
    }
}
