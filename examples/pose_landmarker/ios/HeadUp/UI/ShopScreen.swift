import SwiftUI

struct ShopScreen: View {
    @ObservedObject var store: HeadUpStore
    @State private var purchasedName: String?
    private var copy: HeadUpCopy { HeadUpCopy(language: store.language) }

    var body: some View {
        HeadUpPage {
            HeadUpSectionTitle(title: copy.text("Good habits, well rewarded", "好習慣，值得獎勵"), symbol: "bag")
            HeadUpCard {
                HStack(spacing: 18) {
                    Image(systemName: "star.circle.fill")
                        .font(.system(size: 48)).foregroundStyle(HeadUpStyle.warning)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(copy.text("Health points", "健康積分")).foregroundStyle(HeadUpStyle.secondary)
                        Text(store.coins.formatted()).font(.largeTitle.bold()).monospacedDigit().foregroundStyle(HeadUpStyle.warning)
                    }
                    Spacer()
                }
            }
            HeadUpSectionTitle(title: copy.text("Vision Dragon collection", "視力龍收藏"), symbol: "sparkles")
            ForEach(store.shopItems.filter { $0.id == "starlight_armor" || $0.id == "ocean_background" }) { item in
                shopRow(item)
            }
            HeadUpSectionTitle(title: copy.text("Rewards", "獎勵兌換"), symbol: "gift")
            ForEach(store.shopItems.filter { $0.id == "eye_time_ticket" || $0.id == "focus_badge" }) { item in
                shopRow(item)
            }
            Text(copy.text("Earn points by completing daily tasks. Rewards are saved to your collection; no payment is required.", "完成每日任務即可累積積分。兌換項目會儲存於你的收藏，不需要付款。"))
                .font(.footnote).foregroundStyle(HeadUpStyle.secondary)
        }
        .accessibilityIdentifier("shopScreen")
        .alert(copy.text("Added to your collection", "已加入你的收藏"), isPresented: Binding(get: { purchasedName != nil }, set: { if !$0 { purchasedName = nil } })) {
            Button(copy.text("Done", "完成"), role: .cancel) { purchasedName = nil }
        } message: {
            Text(purchasedName ?? "")
        }
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
                HStack {
                    Label(copy.text("\(item.cost) points", "\(item.cost) 點"), systemImage: "star.fill")
                        .font(.subheadline.bold()).foregroundStyle(HeadUpStyle.warning)
                    Spacer()
                    Button {
                        if store.purchaseItem(item.id) { purchasedName = title(item.id) }
                    } label: {
                        Label(buttonTitle(item), systemImage: item.isOwned ? "checkmark" : "gift")
                            .font(.subheadline.bold())
                    }
                    .buttonStyle(.bordered)
                    .tint(item.isOwned ? HeadUpStyle.safe : HeadUpStyle.cyan)
                    .disabled(item.isOwned || store.coins < item.cost)
                    .accessibilityIdentifier("purchase_\(item.id)")
                }
            }
        }
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
        case "starlight_armor": return copy.text("A Vision Dragon collectible", "視力龍造型收藏")
        case "ocean_background": return copy.text("An ocean-inspired collectible", "海藍柔光主題收藏")
        case "eye_time_ticket": return copy.text("A 30-minute entertainment reward", "30 分鐘娛樂時間獎勵")
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
