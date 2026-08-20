package com.example.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class HabitIconItem(
    val id: String,
    val name: String,
    val category: String,
    val keywords: List<String>,
    val vector: ImageVector
)

object IconHelper {
    val iconGallery: List<HabitIconItem> = listOf(
        // Salud & Deporte
        HabitIconItem("fitness", "Gimnasio / Pesas", "Salud & Deporte", listOf("pesas", "gym", "musculo", "entrenamiento", "fitness", "fuerza"), Icons.Default.FitnessCenter),
        HabitIconItem("directions_run", "Correr / Running", "Salud & Deporte", listOf("correr", "cardio", "trote", "atletismo", "run"), Icons.Default.DirectionsRun),
        HabitIconItem("directions_walk", "Caminar / Pasos", "Salud & Deporte", listOf("caminar", "pasear", "pasos", "walk"), Icons.Default.DirectionsWalk),
        HabitIconItem("directions_bike", "Ciclismo / Bici", "Salud & Deporte", listOf("bici", "bicicleta", "ciclismo", "bike"), Icons.Default.DirectionsBike),
        HabitIconItem("pool", "Natación", "Salud & Deporte", listOf("nadar", "natacion", "piscina", "agua"), Icons.Default.Pool),
        HabitIconItem("sports_soccer", "Fútbol / Deporte", "Salud & Deporte", listOf("futbol", "balon", "partido", "juego"), Icons.Default.SportsSoccer),
        HabitIconItem("sports_basketball", "Baloncesto", "Salud & Deporte", listOf("basket", "baloncesto", "canasta"), Icons.Default.SportsBasketball),
        HabitIconItem("sports_tennis", "Tenis / Pádel", "Salud & Deporte", listOf("tenis", "padel", "raqueta"), Icons.Default.SportsTennis),
        HabitIconItem("bolt", "Energía / Fuerza", "Salud & Deporte", listOf("energia", "fuerza", "vitalidad", "rayo"), Icons.Default.Bolt),
        HabitIconItem("accessibility", "Estiramientos / Postura", "Salud & Deporte", listOf("estirar", "postura", "cuerpo", "flexibilidad"), Icons.Default.Accessibility),
        HabitIconItem("favorite", "Salud Cardiovascular", "Salud & Deporte", listOf("corazon", "salud", "cardio", "amor", "vida"), Icons.Default.Favorite),
        HabitIconItem("medical_services", "Medicina / Vitaminas", "Salud & Deporte", listOf("pastillas", "vitaminas", "medico", "salud"), Icons.Default.MedicalServices),

        // Nutrición & Hidratación
        HabitIconItem("water", "Beber Agua", "Nutrición & Hidratación", listOf("agua", "hidratacion", "vaso", "litros", "gota"), Icons.Default.WaterDrop),
        HabitIconItem("coffee", "Café / Desayuno", "Nutrición & Hidratación", listOf("cafe", "taza", "despertar", "manana"), Icons.Default.Coffee),
        HabitIconItem("restaurant", "Alimentación Saludable", "Nutrición & Hidratación", listOf("comer", "dieta", "saludable", "comida", "cena", "almuerzo"), Icons.Default.Restaurant),
        HabitIconItem("lunch_dining", "Nutrición / Dieta", "Nutrición & Hidratación", listOf("nutricion", "alimentos", "macros", "comida"), Icons.Default.LunchDining),
        HabitIconItem("local_cafe", "Té / Infusión", "Nutrición & Hidratación", listOf("te", "infusion", "caliente", "calma"), Icons.Default.LocalCafe),
        HabitIconItem("kitchen", "Cocinar en Casa", "Nutrición & Hidratación", listOf("cocinar", "chef", "receta", "hogar"), Icons.Default.Kitchen),

        // Mente, Zen & Bienestar
        HabitIconItem("meditation", "Meditación / Mindfulness", "Mente & Zen", listOf("meditar", "paz", "zen", "mindfulness", "respirar", "calma"), Icons.Default.SelfImprovement),
        HabitIconItem("psychology", "Salud Mental / Reflexión", "Mente & Zen", listOf("mente", "pensar", "psicologia", "cerebro", "introspeccion"), Icons.Default.Psychology),
        HabitIconItem("spa", "Relax / Cuidado Personal", "Mente & Zen", listOf("spa", "relax", "cuidado", "skincare", "masaje"), Icons.Default.Spa),
        HabitIconItem("sun", "Rutina de Mañana / Sol", "Mente & Zen", listOf("sol", "manana", "despertar", "luz", "dia"), Icons.Default.WbSunny),
        HabitIconItem("moon", "Sueño / Rutina Nocturna", "Mente & Zen", listOf("noche", "dormir", "sueno", "descanso", "luna"), Icons.Default.Bedtime),
        HabitIconItem("wb_twilight", "Atardecer / Desconexión", "Mente & Zen", listOf("atardecer", "desconectar", "tarde"), Icons.Default.WbTwilight),
        HabitIconItem("nature", "Paseo al Aire Libre", "Mente & Zen", listOf("naturaleza", "bosque", "arbol", "aire libre"), Icons.Default.Nature),
        HabitIconItem("sentiment_very_satisfied", "Gratitud & Felicidad", "Mente & Zen", listOf("gratitud", "feliz", "positivo", "sonrisa"), Icons.Default.SentimentVerySatisfied),

        // Productividad & Trabajo
        HabitIconItem("timer", "Enfoque / Pomodoro", "Productividad", listOf("tiempo", "cronometro", "pomodoro", "reloj", "focus"), Icons.Default.Timer),
        HabitIconItem("alarm", "Despertador / Puntualidad", "Productividad", listOf("alarma", "madrugar", "hora", "puntual"), Icons.Default.Alarm),
        HabitIconItem("work", "Trabajo Profesional", "Productividad", listOf("trabajo", "oficina", "empresa", "proyecto"), Icons.Default.Work),
        HabitIconItem("check_circle", "Completar Tareas", "Productividad", listOf("check", "listo", "completado", "tarea"), Icons.Default.CheckCircle),
        HabitIconItem("task_alt", "Prioridades del Día", "Productividad", listOf("prioridad", "objetivo", "meta", "done"), Icons.Default.TaskAlt),
        HabitIconItem("center_focus_strong", "Trabajo Profundo (Deep Work)", "Productividad", listOf("deep work", "concentracion", "enfoque", "diana"), Icons.Default.CenterFocusStrong),
        HabitIconItem("schedule", "Gestión del Tiempo", "Productividad", listOf("horario", "agenda", "bloque", "tiempo"), Icons.Default.Schedule),
        HabitIconItem("checklist", "Lista de Verificación", "Productividad", listOf("checklist", "pasos", "rutina"), Icons.Default.Checklist),

        // Estudio, Lectura & Tecnología
        HabitIconItem("book", "Lectura de Libros", "Estudio & Dev", listOf("libro", "leer", "lectura", "paginas"), Icons.AutoMirrored.Filled.MenuBook),
        HabitIconItem("code", "Programación / Código", "Estudio & Dev", listOf("codigo", "programar", "terminal", "software", "dev"), Icons.Default.Terminal),
        HabitIconItem("laptop", "Desarrollo / Proyecto Digital", "Estudio & Dev", listOf("laptop", "computadora", "pc", "tecnologia"), Icons.Default.Laptop),
        HabitIconItem("school", "Cursos & Aprendizaje", "Estudio & Dev", listOf("estudiar", "universidad", "curso", "aprender"), Icons.Default.School),
        HabitIconItem("language", "Aprender Idiomas", "Estudio & Dev", listOf("idiomas", "ingles", "frances", "lenguaje", "mundo"), Icons.Default.Language),
        HabitIconItem("edit_note", "Diario / Journaling / Escribir", "Estudio & Dev", listOf("escribir", "diario", "journal", "notas", "redactar"), Icons.Default.EditNote),
        HabitIconItem("lightbulb", "Generar Ideas / Innovación", "Estudio & Dev", listOf("idea", "bombilla", "creatividad", "pensar"), Icons.Default.Lightbulb),
        HabitIconItem("science", "Ciencia & Investigación", "Estudio & Dev", listOf("ciencia", "experimento", "quimica", "investigar"), Icons.Default.Science),

        // Creatividad & Hobbies
        HabitIconItem("brush", "Pintura & Arte", "Creatividad", listOf("arte", "pintar", "dibujar", "pincel", "crear"), Icons.Default.Brush),
        HabitIconItem("music", "Tocar Música / Instrumento", "Creatividad", listOf("musica", "guitarra", "piano", "cantar", "cancion"), Icons.Default.MusicNote),
        HabitIconItem("photo_camera", "Fotografía", "Creatividad", listOf("foto", "camara", "video", "imagen"), Icons.Default.PhotoCamera),
        HabitIconItem("sports_esports", "Videojuegos / Ocio Moderado", "Creatividad", listOf("gaming", "jugar", "videojuegos", "consola"), Icons.Default.SportsEsports),
        HabitIconItem("palette", "Diseño Visual", "Creatividad", listOf("diseno", "colores", "paleta", "creativo"), Icons.Default.Palette),
        HabitIconItem("movie", "Cine & Documentales", "Creatividad", listOf("cine", "pelicula", "documental", "video"), Icons.Default.Movie),

        // Finanzas & Crecimiento
        HabitIconItem("savings", "Ahorrar Dinero", "Finanzas", listOf("ahorro", "alcancia", "guardar", "dinero"), Icons.Default.Savings),
        HabitIconItem("payments", "Presupuesto / Finanzas", "Finanzas", listOf("pagos", "presupuesto", "gastos", "tarjeta"), Icons.Default.Payments),
        HabitIconItem("trending_up", "Inversiones / Métricas", "Finanzas", listOf("inversion", "crecimiento", "grafica", "finanzas"), Icons.AutoMirrored.Filled.TrendingUp),
        HabitIconItem("account_balance", "Patrimonio / Banco", "Finanzas", listOf("banco", "patrimonio", "finanzas"), Icons.Default.AccountBalance),

        // Hogar, Social & Estilo de Vida
        HabitIconItem("cleaning_services", "Limpieza & Orden", "Hogar & Vida", listOf("limpieza", "orden", "limpiar", "casa", "aspirar"), Icons.Default.CleaningServices),
        HabitIconItem("home", "Hogar & Familia", "Hogar & Vida", listOf("casa", "familia", "hogar", "convivencia"), Icons.Default.Home),
        HabitIconItem("pets", "Pasear Mascota / Perro", "Hogar & Vida", listOf("perro", "gato", "mascota", "animal"), Icons.Default.Pets),
        HabitIconItem("groups", "Socializar / Llamar Amigos", "Hogar & Vida", listOf("amigos", "llamar", "social", "familia", "contacto"), Icons.Default.Groups),
        HabitIconItem("emoji_events", "Metas & Victorias", "Hogar & Vida", listOf("victoria", "trofeo", "premio", "exito"), Icons.Default.EmojiEvents),
        HabitIconItem("star", "Hábito Estrella", "Hogar & Vida", listOf("estrella", "favorito", "destacado"), Icons.Default.Star),

        // Gamificación, Niveles & Insignias
        HabitIconItem("sprout", "Brote / Inicio", "Gamificación", listOf("brote", "planta", "inicio", "semilla", "sprout"), Icons.Default.Eco),
        HabitIconItem("local_fire_department", "Fuego / Racha", "Gamificación", listOf("fuego", "racha", "llama", "ardor"), Icons.Default.LocalFireDepartment),
        HabitIconItem("military_tech", "Medalla / Rango", "Gamificación", listOf("medalla", "militar", "rango", "disciplina"), Icons.Default.MilitaryTech),
        HabitIconItem("shield", "Escudo / Fortaleza", "Gamificación", listOf("escudo", "defensa", "fortaleza", "resistencia"), Icons.Default.Shield),
        HabitIconItem("auto_awesome", "Destellos / Magia", "Gamificación", listOf("magia", "destellos", "brillo", "arquitecto"), Icons.Default.AutoAwesome),
        HabitIconItem("workspace_premium", "Premio / Excelencia", "Gamificación", listOf("premio", "insignia", "premium", "medalla"), Icons.Default.WorkspacePremium),
        HabitIconItem("diamond", "Diamante / Maestro", "Gamificación", listOf("diamante", "joya", "maestro", "zen"), Icons.Default.Diamond),
        HabitIconItem("stars", "Estrellas / Leyenda", "Gamificación", listOf("estrellas", "galaxia", "leyenda", "triunfo"), Icons.Default.Stars),
        HabitIconItem("rocket_launch", "Cohete / Despegue", "Gamificación", listOf("cohete", "lanzamiento", "despegue", "comienzo"), Icons.Default.RocketLaunch)
    )

    val availableIcons: Map<String, ImageVector> = iconGallery.associate { it.id to it.vector } + mapOf(
        "eco" to Icons.Default.Eco,
        "fire" to Icons.Default.LocalFireDepartment,
        "flame" to Icons.Default.LocalFireDepartment,
        "trophy" to Icons.Default.EmojiEvents,
        "medal" to Icons.Default.MilitaryTech,
        "badge" to Icons.Default.WorkspacePremium,
        "rocket" to Icons.Default.RocketLaunch
    )

    val categories: List<String> = listOf("Todas") + iconGallery.map { it.category }.distinct()

    fun getIconByName(name: String): ImageVector {
        val found = iconGallery.find { it.id.equals(name, ignoreCase = true) }
        return found?.vector ?: availableIcons[name] ?: Icons.Default.CheckCircle
    }

    fun getIconItemByName(name: String): HabitIconItem? {
        return iconGallery.find { it.id.equals(name, ignoreCase = true) }
    }
}
