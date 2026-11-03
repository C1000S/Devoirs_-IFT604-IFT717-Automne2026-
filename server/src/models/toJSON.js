// Transformation JSON commune aux modèles : expose `id` au lieu de `_id`
// et retire le champ `__v` interne de Mongoose.
const toJSONOptions = {
  virtuals: true,
  versionKey: false,
  transform: (_doc, ret) => {
    ret.id = ret._id;
    delete ret._id;
    return ret;
  },
};

module.exports = toJSONOptions;
