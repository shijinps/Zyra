package com.zyra.app.data.knowledge

data class Topic(
    val subject: String,
    val title: String,
    val definition: String,
    val formula: String? = null,
    val keywords: List<String>
)

object SchoolCurriculum {
    val curriculum = listOf(
        // MATHEMATICS (Priority)
        // -----------------------
        Topic(
            "Mathematics", "Area of a Circle",
            "The area of a circle is the space occupied by the circle.",
            "A = πr² (where r is radius)",
            listOf("area of circle", "circle area", "formula for circle")
        ),
        Topic(
            "Mathematics", "Pythagoras Theorem",
            "In a right-angled triangle, the square of the hypotenuse side is equal to the sum of squares of the other two sides.",
            "a² + b² = c²",
            listOf("pythagoras theorem", "pythagorean", "right triangle")
        ),
        Topic(
            "Mathematics", "Quadratic Formula",
            "Used to find the roots of a quadratic equation ax² + bx + c = 0.",
            "x = (-b ± √(b² - 4ac)) / 2a",
            listOf("quadratic formula", "roots of quadratic", "quadratic equation")
        ),
        Topic(
            "Mathematics", "Volume of a Sphere",
            "The volume occupied by a sphere in 3D space.",
            "V = (4/3)πr³",
            listOf("volume of sphere", "sphere volume")
        ),
        Topic(
            "Mathematics", "Area of Triangle (Heron's Formula)",
            "Area of a triangle when all three sides (a, b, c) are known.",
            "Area = √[s(s-a)(s-b)(s-c)], where s = (a+b+c)/2",
            listOf("heron's formula", "area of triangle", "heron")
        ),
        Topic(
            "Mathematics", "BODMAS Rule",
            "Order of operations: Brackets, Orders (powers/roots), Division, Multiplication, Addition, Subtraction.",
            null,
            listOf("bodmas", "order of operations")
        ),
        Topic(
            "Mathematics", "Trigonometric Ratios",
            "SOH CAH TOA: Sine = Opposite/Hypotenuse, Cosine = Adjacent/Hypotenuse, Tangent = Opposite/Adjacent.",
            "sinθ = O/H, cosθ = A/H, tanθ = O/A",
            listOf("trigonometry", "sin cos tan", "sohcahtoa")
        ),

        // PHYSICS
        // -------
        Topic(
            "Physics", "Ohm's Law",
            "Ohm's law states that the current through a conductor is directly proportional to the voltage across the two points.",
            "V = IR",
            listOf("ohm's law", "ohms law", "voltage current resistance")
        ),
        Topic(
            "Physics", "Newton's Second Law",
            "The force acting on an object is equal to the mass of that object times its acceleration.",
            "F = ma",
            listOf("newton's second law", "force formula", "f=ma")
        ),
        Topic(
            "Physics", "Kinetic Energy",
            "The energy that an object possesses due to its motion.",
            "KE = 1/2 mv²",
            listOf("kinetic energy", "ke")
        ),
        Topic(
            "Physics", "Potential Energy",
            "The energy held by an object because of its position relative to other objects.",
            "PE = mgh",
            listOf("potential energy", "pe")
        ),
        Topic(
            "Physics", "Speed of Light",
            "The speed at which light travels in a vacuum.",
            "c ≈ 3 × 10⁸ m/s",
            listOf("speed of light", "c")
        ),

        // CHEMISTRY
        // ---------
        Topic(
            "Chemistry", "Baking Soda",
            "Sodium Bicarbonate, commonly used in baking.",
            "NaHCO₃",
            listOf("baking soda", "chemical name of baking soda", "sodium bicarbonate")
        ),
        Topic(
            "Chemistry", "Common Salt",
            "Sodium Chloride, used as a condiment and food preservative.",
            "NaCl",
            listOf("common salt", "table salt", "sodium chloride")
        ),
        Topic(
            "Chemistry", "Sulphuric Acid",
            "A strong mineral acid, also known as Oil of Vitriol.",
            "H₂SO₄",
            listOf("sulphuric acid", "king of chemicals")
        ),
        Topic(
            "Chemistry", "Avogadro's Number",
            "The number of constituent particles (atoms of molecules) in one mole of a given substance.",
            "6.022 × 10²³",
            listOf("avogadro number", "mole concept")
        ),
        Topic(
            "Chemistry", "Photosynthesis Formula",
            "The chemical reaction where plants convert light energy into chemical energy.",
            "6CO₂ + 6H₂O + Light → C₆H₁₂O₆ + 6O₂",
            listOf("photosynthesis formula", "photosynthesis reaction")
        ),

        // BIOLOGY
        // -------
        Topic(
            "Biology", "Mitochondria",
            "Double-membrane-bound organelle found in most eukaryotic organisms. Generates most of the cell's supply of adenosine triphosphate (ATP), used as a source of chemical energy.",
            "Known as: Powerhouse of the cell",
            listOf("mitochondria", "powerhouse of cell")
        ),
        Topic(
            "Biology", "Photosynthesis",
            "The process by which green plants and some other organisms use sunlight to synthesize nutrients from carbon dioxide and water.",
            null,
            listOf("photosynthesis definition", "what is photosynthesis")
        ),
        Topic(
            "Biology", "DNA",
            "Deoxyribonucleic acid is a molecule composed of two polynucleotide chains that coil around each other to form a double helix carrying genetic instructions.",
            null,
            listOf("dna", "deoxyribonucleic acid")
        ),
         Topic(
            "Biology", "Human Heart",
            "The heart is a muscular organ that pumps blood through the blood vessels of the circulatory system. It has four chambers: two atria and two ventricles.",
            null,
            listOf("human heart", "heart chambers")
        )

    )

    fun getCurriculumResponse(query: String): String? {
        val lower = query.lowercase().trim()
        
        // Find best match by keywords
        val bestMatch = curriculum.find { topic -> 
            topic.keywords.any { k -> lower.contains(k) } 
        }

        if (bestMatch != null) {
            val formulaText = if (bestMatch.formula != null) " The formula is: ${bestMatch.formula}." else ""
            return "[${bestMatch.subject}] ${bestMatch.title}: ${bestMatch.definition}$formulaText"
        }
        
        return null
    }
}
