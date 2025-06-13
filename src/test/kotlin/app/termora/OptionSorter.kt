package app.termora
import app.termora.OptionsPane.Anchor
import app.termora.OptionsPane.Option

class OptionSorter {

    fun sortOptions(options: List<Option>): List<Option> {
        if (options.isEmpty()) return emptyList()

        // 如果所有选项的 Anchor 都是 Null，则不排序，保持原始顺序
        if (options.all { it.getAnchor() is Anchor.Null }) {
            return options
        }

        val optionMap = options.associateBy { it.getIdentifier() }
        val result = mutableListOf<Option>()

        // 分组处理不同类型的锚点
        val nullOptions = options.filter { it.getAnchor() is Anchor.Null }
        val firstOptions = options.filter { it.getAnchor() is Anchor.First }
        val lastOptions = options.filter { it.getAnchor() is Anchor.Last }

        // 分离有效和无效的相对位置选项
        val (validRelativeOptions, invalidRelativeOptions) = options.filter {
            it.getAnchor() is Anchor.Before || it.getAnchor() is Anchor.After
        }.partition { option ->
            when (val anchor = option.getAnchor()) {
                is Anchor.Before -> optionMap.containsKey(anchor.target)
                is Anchor.After -> optionMap.containsKey(anchor.target)
                else -> false
            }
        }

        // 收集所有需要参与相对排序的选项，包括它们的目标选项
        val targetIds = validRelativeOptions.mapNotNull { option ->
            when (val anchor = option.getAnchor()) {
                is Anchor.Before -> anchor.target
                is Anchor.After -> anchor.target
                else -> null
            }
        }.toSet()

        val targetOptions = targetIds.mapNotNull { targetId -> optionMap[targetId] }
        val allOptionsToSort = (validRelativeOptions + nullOptions + targetOptions).distinctBy { it.getIdentifier() }
        val sortedRelativeAndNull = sortRelativeOptions(allOptionsToSort, optionMap)

        // 按优先级组合结果，但要排除已经在相对排序中处理过的选项
        val sortedIds = sortedRelativeAndNull.map { it.getIdentifier() }.toSet()

        result.addAll(firstOptions.filter { it.getIdentifier() !in sortedIds }.sortedBy { it.getIdentifier() })
        result.addAll(sortedRelativeAndNull)
        result.addAll(invalidRelativeOptions.sortedBy { it.getIdentifier() })
        result.addAll(lastOptions.filter { it.getIdentifier() !in sortedIds }.sortedBy { it.getIdentifier() })

        return result
    }

    private fun sortRelativeOptions(options: List<Option>, optionMap: Map<String, Option>): List<Option> {
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        val result = mutableListOf<Option>()
        val localOptionMap = options.associateBy { it.getIdentifier() }

        fun dfs(optionId: String): Boolean {
            if (visiting.contains(optionId)) {
                return false // 循环依赖
            }

            if (visited.contains(optionId)) {
                return true
            }

            visiting.add(optionId)
            val option = localOptionMap[optionId] ?: return false

            // 先处理所有必须在当前选项之前的选项
            options.forEach { otherOption ->
                val otherAnchor = otherOption.getAnchor()
                val otherId = otherOption.getIdentifier()

                // 如果其他选项声明要在当前选项之前（Before），先处理其他选项
                if (otherAnchor is Anchor.Before && otherAnchor.target == optionId && !visited.contains(otherId)) {
                    dfs(otherId)
                }

                // 如果当前选项声明要在其他选项之后（After），先处理其他选项
                if (option.getAnchor() is Anchor.After &&
                    (option.getAnchor() as Anchor.After).target == otherId &&
                    !visited.contains(otherId)) {
                    dfs(otherId)
                }
            }

            visiting.remove(optionId)
            visited.add(optionId)
            result.add(option)

            return true
        }

        // 开始排序 - 按照原始顺序处理，确保稳定排序
        options.forEach { option ->
            if (!visited.contains(option.getIdentifier())) {
                dfs(option.getIdentifier())
            }
        }

        return result
    }
}

// 使用示例
fun main() {
    // 创建测试选项
    class TestOption(
        private val title: String,
        private val identifier: String,
        private val anchor: Anchor
    ) : Option {
        override fun getIcon(isSelected: Boolean) = TODO("Not implemented")
        override fun getTitle() = title
        override fun getJComponent() = TODO("Not implemented")
        override fun getIdentifier() = identifier
        override fun getAnchor() = anchor
    }

    val options = listOf(
        TestOption("Appearance", "Appearance", Anchor.Null),
        TestOption("Before Appearance", "BeforeApp", Anchor.Before("Appearance")),
        TestOption("After Appearance", "AfterApp", Anchor.After("Appearance")),
        TestOption("First Option", "First", Anchor.First),
        TestOption("Last Option", "Last", Anchor.Last),
        TestOption("Another Null", "AnotherNull", Anchor.Null),
        TestOption("Invalid Target", "Invalid", Anchor.Before("NonExistent"))
    )

    val sorter = OptionSorter()
    val sortedOptions = sorter.sortOptions(options)

    println("排序结果:")
    sortedOptions.forEachIndexed { index, option ->
        println("${index + 1}. ${option.getTitle()} (${option.getIdentifier()}) - ${option.getAnchor()}")
    }

    println("\n预期结果说明:")
    println("- First Option 应该在最前面")
    println("- Before Appearance 应该在 Appearance 前面")
    println("- After Appearance 应该在 Appearance 后面")
    println("- Null 选项(Appearance, Another Null)保持原始相对顺序")
    println("- Invalid Target 应该在 Null 选项后面")
    println("- Last Option 应该在最后面")

    println("\n测试全部为 Null 的情况:")
    val nullOnlyOptions = listOf(
        TestOption("Option X", "X", Anchor.Null),
        TestOption("Option Y", "Y", Anchor.Null),
        TestOption("Option Z", "Z", Anchor.Null)
    )

    val sortedNullOnly = sorter.sortOptions(nullOnlyOptions)
    println("原始顺序: ${nullOnlyOptions.map { it.getTitle() }}")
    println("排序后: ${sortedNullOnly.map { it.getTitle() }}")
    println("是否保持原始顺序: ${nullOnlyOptions == sortedNullOnly}")

    // 额外测试复杂的依赖关系
    println("\n测试复杂依赖关系:")
    val complexOptions = listOf(
        TestOption("A", "A", Anchor.Null),
        TestOption("B", "B", Anchor.After("A")),
        TestOption("C", "C", Anchor.Before("A")),
        TestOption("D", "D", Anchor.After("B")),
        TestOption("E", "E", Anchor.Before("C"))
    )

    val sortedComplex = sorter.sortOptions(complexOptions)
    println("复杂依赖排序结果:")
    sortedComplex.forEachIndexed { index, option ->
        println("${index + 1}. ${option.getTitle()} - ${option.getAnchor()}")
    }
    // 额外测试：Before/After 指向不同优先级的选项
    println("\n测试 Before/After 指向不同优先级的选项:")
    val mixedPriorityOptions = listOf(
        TestOption("A", "A", Anchor.Null),
        TestOption("B", "B", Anchor.Before("D")),  // B Before D (D 是 Last)
        TestOption("C", "C", Anchor.After("F")),   // C After F (F 是 First)
        TestOption("D", "D", Anchor.Last),
        TestOption("E", "E", Anchor.Null),
        TestOption("F", "F", Anchor.First)
    )

    val sortedMixed = sorter.sortOptions(mixedPriorityOptions)
    println("混合优先级排序结果:")
    sortedMixed.forEachIndexed { index, option ->
        println("${index + 1}. ${option.getTitle()} - ${option.getAnchor()}")
    }
    println("预期: F -> C -> A -> E -> B -> D")
}